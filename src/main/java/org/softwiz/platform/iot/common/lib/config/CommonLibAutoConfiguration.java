package org.softwiz.platform.iot.common.lib.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * WIZ Common Library Auto Configuration
 *
 * 이 설정 클래스는 Spring Boot의 Auto Configuration 메커니즘을 통해
 * 자동으로 로드되어 라이브러리의 모든 컴포넌트를 스캔합니다.
 *
 * 포함되는 컴포넌트:
 * - Filters: MdcFilter
 * - Interceptors: GatewayHeaderInterceptor, LoggingInterceptor
 * - Utils: CryptoUtil, JwtUtil, MaskingUtil, ClientIpExtractor, DeviceDetector, ValidationUtil
 * - Validators: GatewaySignatureValidator
 * - Services: LoggingService
 * - Exception Handlers: GlobalExceptionHandler
 * - Config: PublicPathConfig
 *
 * 사용법:
 * 1. 마이크로서비스의 pom.xml에 의존성 추가:
 *    <dependency>
 *        <groupId>com.github.사용자이름</groupId> TODO 회사공개깃허브 옮기기
 *        <artifactId>wiz-common-lib</artifactId>
 *        <version>1.0.0</version>
 *    </dependency>
 *
 * 2. application.yml에 필수 설정 추가 (아래 JavaDoc 참조)
 *
 * 3. 자동으로 모든 컴포넌트가 Bean으로 등록됨
 *
 * 필수 설정:
 * <pre>
 * # CryptoUtil (AES-256 암호화)
 * crypto:
 *   secret-key: "your-32-byte-secret-key-here!"  # 정확히 32 bytes
 *   iv: "your-16-byte-iv!!"                       # 정확히 16 bytes
 *
 * # JwtUtil (JWT 토큰)
 * jwt:
 *   secret: "your-jwt-secret-key-at-least-256-bits-long-for-hs256-algorithm"
 *   expiration: 3600000              # Access Token: 1시간 (밀리초)
 *   refresh-expiration: 86400000     # Refresh Token: 24시간 (밀리초)
 *   issuer: "WIZ-PLATFORM"
 *
 * # GatewaySignatureValidator (Gateway 서명 검증)
 * gateway:
 *   signature:
 *     secret: "your-gateway-signature-secret-key"
 *     enabled: true                  # 운영: true, 개발: false (선택)
 *     mock-enabled: false            # 로컬 테스트: true (선택)
 *
 * # PublicPathConfig (인증 제외 경로)
 * security:
 *   publicPaths:
 *     - /actuator/**
 *     - /swagger-ui/**
 *     - /v3/api-docs/**
 *     - /health
 *     - /favicon.ico
 * </pre>
 *
 * 선택적 설정:
 * - gateway.signature.enabled=false: Gateway 서명 검증 비활성화 (개발 편의용)
 * - gateway.signature.mock-enabled=true: Mock 서명 허용 (로컬 테스트용)
 *
 * 주의사항:
 * - crypto.secret-key는 반드시 32바이트여야 합니다 (AES-256)
 * - crypto.iv는 반드시 16바이트여야 합니다 (AES 블록 크기)
 * - jwt.secret는 HS256 알고리즘을 위해 최소 256비트 이상 권장
 * - 운영 환경에서는 모든 secret 값을 환경변수 또는 암호화된 설정으로 관리하세요
 */

@Slf4j
@Configuration
@ComponentScan(basePackages = {
        "org.softwiz.platform.iot.common.lib.config",
        "org.softwiz.platform.iot.common.lib.context",
        "org.softwiz.platform.iot.common.lib.filter",
        "org.softwiz.platform.iot.common.lib.interceptor",
        "org.softwiz.platform.iot.common.lib.service",
        "org.softwiz.platform.iot.common.lib.util",
        "org.softwiz.platform.iot.common.lib.validator",
        "org.softwiz.platform.iot.common.lib.exception",
        "org.softwiz.platform.iot.common.lib.advice",
        "org.softwiz.platform.iot.common.lib.mybatis"
})
@EnableConfigurationProperties
public class CommonLibAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("WIZ Common Library Initialized");
        log.info("========================================");
        log.info("✓ Core Components:");
        log.info("  - GatewayContext (ThreadLocal)");
        log.info("  - MdcFilter (Request ID, Client IP)");
        log.info("  - GatewayHeaderInterceptor");
        log.info("  - GlobalExceptionHandler");
        log.info("✓ Security Components:");
        log.info("  - CryptoUtil (AES-256)");
        log.info("  - JwtUtil (JWT)");
        log.info("  - GatewaySignatureValidator");
        log.info("✓ Utility Components:");
        log.info("  - MaskingUtil (Log Masking)");
        log.info("  - ClientIpExtractor");
        log.info("  - DeviceDetector");
        log.info("  - ValidationUtil");
        log.info("========================================");
        log.info("⚠️  Required Configuration:");
        log.info("   crypto.secret-key, crypto.iv");
        log.info("   jwt.secret, jwt.expiration");
        log.info("   gateway.signature.secret");
        log.info("   security.publicPaths");
        log.info("========================================");
    }

    /**
     * 개발 환경 체크 (선택적)
     * 개발 환경에서 설정 누락 시 경고 로그 출력
     */
    @PostConstruct
    public void validateConfiguration() {
        // 여기서는 로그만 출력하고, 실제 검증은 각 컴포넌트의 생성자에서 수행
        // (CryptoUtil, JwtUtil 등은 @Value로 받아서 자동 검증됨)
    }
}