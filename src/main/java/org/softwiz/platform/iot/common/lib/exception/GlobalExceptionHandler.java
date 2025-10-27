package org.softwiz.platform.iot.common.lib.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 *
 * 로깅 전략:
 * - WARN: 클라이언트 에러 (400대)
 * - ERROR: 서버 에러 (500대)
 * - MDC에 이미 [requestId] [clientIp] [userId] 설정되어 있음
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /**
     * Validation 예외 처리 (400 Bad Request)
     * 필수 파라미터 누락, 포맷 오류 등
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        String path = extractPath(request);

        // Validation 에러는 클라이언트 실수이므로 WARN 레벨
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
     * 401 Unauthorized 예외 처리
     * 규격서: 에러 코드 없이 "인증실패" 메시지만 사용
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request) {

        String path = extractPath(request);

        // 인증 실패는 보안 이슈일 수 있으므로 WARN 레벨
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
     * 비즈니스 예외 처리
     * 규격서에 명시된 에러 코드만 사용
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {

        HttpStatus status;

        // 규격서 기준 HTTP 상태 코드 매핑
        switch (ex.getCode()) {
            // 400 Bad Request
            case "INVALID_INPUT":
            case "INVALID_CREDENTIALS":
            case "Bad Request":
                status = HttpStatus.BAD_REQUEST;
                break;

            // 401 Unauthorized - UnauthorizedException 사용 권장
            case "UNAUTHORIZED":
                status = HttpStatus.UNAUTHORIZED;
                break;

            // 500 Internal Server Error
            case "INTERNAL_ERROR":
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        String path = extractPath(request);

        // 로그 레벨: 400대는 WARN, 500대는 ERROR
        if (status.is4xxClientError()) {
            log.warn("Business error: {} - {} (Code: {})", path, ex.getMessage(), ex.getCode());
        } else {
            log.error("Business error: {} - {} (Code: {})", path, ex.getMessage(), ex.getCode(), ex);
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
     * 일반 예외 처리 (500 Internal Server Error)
     * 규격서: "서버 에러 - provider 통신 실패 등 예외"
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            WebRequest request) {

        String path = extractPath(request);

        // 예상하지 못한 서버 에러는 ERROR 레벨
        log.error("Unexpected error: {} - {}", path, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .requestId(getRequestId())
                        .code("INTERNAL_ERROR")
                        .message("서버 내부 오류")
                        .path(path)
                        .build());
    }

    /**
     * MDC에서 Request ID 가져오기
     */
    private String getRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "NO_ID";
    }

    /**
     * Request에서 경로 추출
     */
    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        // "uri=/api/login" 형태에서 경로만 추출
        if (description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }
}