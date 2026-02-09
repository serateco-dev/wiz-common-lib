package org.softwiz.platform.iot.common.lib.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.TransactionSystemException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * 스케줄러 공통 설정 (선택적 활성화)
 *
 * 활성화 조건:
 * 1. ShedLock이 클래스패스에 있을 것
 * 2. scheduler.enabled=true (기본값: false, 명시적 활성화 필요)
 *
 * 사용하지 않는 서비스에서는 자동으로 비활성화됩니다.
 */
@Slf4j
@Configuration
@ConditionalOnClass({
        EnableSchedulerLock.class,
        EnableScheduling.class,
        JdbcTemplateLockProvider.class
})
@ConditionalOnProperty(
        prefix = "scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false  // 기본값: false (명시적으로 활성화 필요)
)
public class SchedulerAutoConfiguration {

    @Value("${datasource.readonly.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${datasource.readonly.retry.interval-ms:2000}")
    private long retryIntervalMs;

    /**
     * LockProvider 빈 생성
     *
     * 각 서비스에서 이미 LockProvider를 정의했다면 스킵됩니다.
     */
    @Bean
    @ConditionalOnClass(name = "javax.sql.DataSource")
    public LockProvider commonLockProvider(DataSource dataSource) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            log.info("Creating common LockProvider with DB connection recovery");
            JdbcTemplateLockProvider jdbcProvider = new JdbcTemplateLockProvider(
                    JdbcTemplateLockProvider.Configuration.builder()
                            .withJdbcTemplate(jdbcTemplate)
                            .build()
            );

            return new RecoverableLockProvider(jdbcProvider, dataSource, maxRetryAttempts, retryIntervalMs);

        } catch (Exception e) {
            log.warn("Database connection failed - using NoOpLockProvider. Error: {}", e.getMessage());
            return new NoOpLockProvider();
        }
    }

    /**
     * DB 에러 복구 기능이 있는 LockProvider
     */
    private static class RecoverableLockProvider implements LockProvider {
        private final JdbcTemplateLockProvider delegate;
        private final DataSource dataSource;
        private final int maxRetryAttempts;
        private final long retryIntervalMs;

        public RecoverableLockProvider(JdbcTemplateLockProvider delegate,
                                       DataSource dataSource,
                                       int maxRetryAttempts,
                                       long retryIntervalMs) {
            this.delegate = delegate;
            this.dataSource = dataSource;
            this.maxRetryAttempts = maxRetryAttempts;
            this.retryIntervalMs = retryIntervalMs;
        }

        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
            try {
                return delegate.lock(lockConfiguration);
            } catch (Exception e) {
                if (isRecoverableException(e)) {
                    log.warn("Recoverable database error for lock: {}. Attempting recovery... Error: {}",
                            lockConfiguration.getName(), getErrorMessage(e));

                    if (attemptRecovery()) {
                        try {
                            return delegate.lock(lockConfiguration);
                        } catch (Exception retryEx) {
                            log.warn("Lock acquisition failed after recovery for: {}. Skipping.",
                                    lockConfiguration.getName());
                            return Optional.empty();
                        }
                    }

                    log.warn("Database unavailable, skipping lock for: {}", lockConfiguration.getName());
                    return Optional.empty();
                }
                throw e;
            }
        }

        private boolean isRecoverableException(Exception e) {
            return isReadOnlyException(e) ||
                    isReplicationHookException(e) ||
                    isConnectionException(e) ||
                    e instanceof TransactionSystemException;
        }

        private boolean isReadOnlyException(Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof SQLException) {
                    SQLException sqlEx = (SQLException) cause;
                    if (sqlEx.getErrorCode() == 1290) {
                        return true;
                    }
                }
                String message = cause.getMessage();
                if (message != null &&
                        (message.contains("read-only") ||
                                message.contains("READ-ONLY") ||
                                message.contains("error code [1290]"))) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }

        private boolean isReplicationHookException(Exception e) {
            Throwable cause = e;
            while (cause != null) {
                String message = cause.getMessage();
                if (message != null &&
                        (message.contains("replication hook") ||
                                message.contains("before_commit") ||
                                message.contains("Error on observer"))) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }

        private boolean isConnectionException(Exception e) {
            Throwable cause = e;
            while (cause != null) {
                String message = cause.getMessage();
                if (message != null) {
                    if (message.contains("Connection refused") ||
                            message.contains("Communications link failure") ||
                            message.contains("Connection is not available") ||
                            message.contains("HikariPool") ||
                            message.contains("CannotCreateTransactionException")) {
                        return true;
                    }
                }

                String className = cause.getClass().getName();
                if (className.contains("CommunicationsException") ||
                        className.contains("SQLTransientConnectionException") ||
                        className.contains("ConnectException")) {
                    return true;
                }

                cause = cause.getCause();
            }
            return false;
        }

        private String getErrorMessage(Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause.getMessage() != null) {
                    return cause.getMessage();
                }
                cause = cause.getCause();
            }
            return e.getClass().getSimpleName();
        }

        private boolean attemptRecovery() {
            if (!(dataSource instanceof HikariDataSource)) {
                return false;
            }

            HikariDataSource hikariDS = (HikariDataSource) dataSource;

            for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
                try {
                    log.info("Recovery attempt {}/{} - evicting connections...",
                            attempt, maxRetryAttempts);

                    hikariDS.getHikariPoolMXBean().softEvictConnections();
                    Thread.sleep(retryIntervalMs);

                    if (isReadWriteMode()) {
                        log.info("Successfully recovered to READ-WRITE mode");
                        return true;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception e) {
                    log.debug("Recovery attempt {} failed: {}", attempt, e.getMessage());
                }
            }

            log.warn("Failed to recover after {} attempts", maxRetryAttempts);
            return false;
        }

        private boolean isReadWriteMode() {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@read_only, @@super_read_only")) {

                if (rs.next()) {
                    boolean readOnly = rs.getBoolean(1);
                    boolean superReadOnly = rs.getBoolean(2);
                    return !readOnly && !superReadOnly;
                }
            } catch (Exception e) {
                log.debug("Failed to check mode: {}", e.getMessage());
            }
            return false;
        }
    }

    /**
     * DB 없이도 작동하는 더미 LockProvider
     */
    private static class NoOpLockProvider implements LockProvider {
        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
            return Optional.of(new SimpleLock() {
                @Override
                public void unlock() {
                    // do nothing
                }
            });
        }
    }
}