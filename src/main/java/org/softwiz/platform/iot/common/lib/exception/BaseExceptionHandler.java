package org.softwiz.platform.iot.common.lib.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * 베이스 예외 처리기
 *
 * 각 서비스에서 상속받아 사용
 * 공통 로직 제공 + 확장 가능
 */
@Slf4j
public abstract class BaseExceptionHandler {

    /**
     * Validation 예외 (공통)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        String path = extractPath(request);
        log.warn("Validation failed: {} - {}", path, errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("Bad Request")
                        .message("잘못된 요청, 필수 파라미터 없음, 포맷 오류")
                        .path(path)
                        .build());
    }

    /**
     * 401 Unauthorized (공통)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request) {

        String path = extractPath(request);
        log.warn("Unauthorized access: {} - {}", path, ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("Unauthorized")
                        .message(ex.getMessage())
                        .path(path)
                        .build());
    }

    /**
     * 비즈니스 예외 (공통)
     *
     * BusinessException에서 제공하는 HttpStatus 사용
     * 기본값은 500, 필요시 예외 생성 시 HttpStatus 지정 가능
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {

        HttpStatus status = ex.getHttpStatus();
        String path = extractPath(request);

        if (status.is4xxClientError()) {
            log.warn("Business error: {} - {} (Code: {}, Status: {})",
                    path, ex.getMessage(), ex.getCode(), status.value());
        } else {
            log.error("Business error: {} - {} (Code: {}, Status: {})",
                    path, ex.getMessage(), ex.getCode(), status.value(), ex);
        }

        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .path(path)
                        .build());
    }

    /**
     * 데이터베이스 연결 실패 예외 (공통)
     * MySQL 서버 연결 불가, 네트워크 오류 등
     */
    @ExceptionHandler({
            CannotGetJdbcConnectionException.class,
            TransientDataAccessResourceException.class
    })
    public ResponseEntity<ErrorResponse> handleDatabaseConnectionException(
            Exception ex,
            WebRequest request) {

        String path = extractPath(request);
        log.error("Database connection failed: {} - {}", path, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("SERVICE_UNAVAILABLE")
                        .message("데이터베이스 연결에 실패했습니다. 잠시 후 다시 시도해주세요.")
                        .path(path)
                        .build());
    }

    /**
     * 데이터베이스 작업 실패 예외 (공통)
     * SQL 실행 오류, 트랜잭션 오류 등
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(
            DataAccessException ex,
            WebRequest request) {

        String path = extractPath(request);
        log.error("Database operation failed: {} - {}", path, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("DATABASE_ERROR")
                        .message("데이터베이스 작업 중 오류가 발생했습니다.")
                        .path(path)
                        .build());
    }

    /**
     * 일반 예외 (공통)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            WebRequest request) {

        String path = extractPath(request);
        log.error("Unexpected error: {} - {}", path, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("INTERNAL_ERROR")
                        .message("서버 내부 오류")
                        .path(path)
                        .build());
    }

    // 유틸리티 메서드
    protected String getRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "NO_ID";
    }

    protected String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        if (description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }
}