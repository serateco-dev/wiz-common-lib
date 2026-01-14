package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.softwiz.platform.iot.common.lib.dto.ApiResponse;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.softwiz.platform.iot.common.lib.validator.GatewaySignatureValidator;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 이메일 서비스 클라이언트
 *
 * <p>다른 마이크로서비스에서 WizMessage 이메일 서비스를 호출할 때 사용합니다.</p>
 * <p>내부 서비스 간 통신을 위해 항상 새로운 Gateway 서명을 생성합니다.</p>
 *
 * <h3>헤더 처리:</h3>
 * <p>실제 호출할 method와 uri로 서명을 생성하여 전달합니다.</p>
 * <p>다른 서비스의 Gateway 헤더를 복사하지 않습니다.</p>
 *
 * <h3>보안 참고:</h3>
 * <p>K8s NetworkPolicy로 같은 네임스페이스 내 Pod 간 통신만 허용되므로,
 * 외부에서 직접 마이크로서비스 API 호출은 불가능합니다.</p>
 *
 * <pre>{@code
 * @Configuration
 * public class EmailClientConfig {
 *     @Value("${microservice.message.url:http://wizmessage:8098}")
 *     private String messageServiceUrl;
 *
 *     @Bean
 *     public EmailClient emailClient(RestTemplate restTemplate,
 *                                     GatewaySignatureValidator signatureValidator) {
 *         return new EmailClient(restTemplate, messageServiceUrl, signatureValidator);
 *     }
 * }
 * }</pre>
 */
@Slf4j
public class EmailClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final GatewaySignatureValidator signatureValidator;

    private static final String EMAIL_SEND_PATH = "/api/v2/email/send";
    private static final String EMAIL_TEMPLATE_SEND_PATH = "/api/v2/email/template/send";
    private static final String EMAIL_VERIFY_SEND_PATH = "/api/v2/email/verify/send";

    /**
     * 생성자 (GatewaySignatureValidator 포함)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 이메일 서비스 기본 URL (예: http://wizmessage:8098)
     * @param signatureValidator Gateway 서명 검증/생성기
     */
    public EmailClient(RestTemplate restTemplate, String baseUrl, GatewaySignatureValidator signatureValidator) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signatureValidator = signatureValidator;
    }

    /**
     * 생성자 (하위 호환성 유지)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 이메일 서비스 기본 URL
     * @deprecated signatureValidator를 포함하는 생성자 사용 권장
     */
    @Deprecated
    public EmailClient(RestTemplate restTemplate, String baseUrl) {
        this(restTemplate, baseUrl, null);
    }

    // ========================================
    // 일반 이메일 발송
    // ========================================

    /**
     * 이메일 발송
     *
     * @param request 이메일 요청
     * @return 발송 결과
     */
    public EmailResult send(EmailUtil.EmailRequest request) {
        return send(request, null);
    }

    /**
     * 이메일 발송 (인증 토큰 포함)
     *
     * @param request 이메일 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public EmailResult send(EmailUtil.EmailRequest request, String accessToken) {
        String url = baseUrl + EMAIL_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", EMAIL_SEND_PATH);
            HttpEntity<EmailUtil.EmailRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            // 성공 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                Long emailId = null;
                String status = null;

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    emailId = dataMap.get("emailId") != null ? ((Number) dataMap.get("emailId")).longValue() : null;
                    status = (String) dataMap.get("status");
                }

                return EmailResult.success(emailId, status, body.getMessage());
            }

            return EmailResult.failure("EMAIL_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            // 4xx, 5xx 에러 처리
            log.error("이메일 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpError(ex, "이메일 발송");

        } catch (ResourceAccessException ex) {
            // 네트워크 타임아웃, 연결 실패
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            // 기타 RestClient 예외
            log.error("이메일 발송 중 RestClient 예외 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SEND_ERROR", "이메일 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            // 예상치 못한 예외
            log.error("이메일 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SEND_ERROR", "이메일 발송 중 오류가 발생했습니다.");
        }
    }

    // ========================================
    // 템플릿 이메일 발송
    // ========================================

    /**
     * 템플릿 기반 이메일 발송
     *
     * @param request 템플릿 이메일 요청
     * @return 발송 결과
     */
    public EmailResult sendTemplate(EmailUtil.TemplateEmailRequest request) {
        return sendTemplate(request, null);
    }

    /**
     * 템플릿 기반 이메일 발송 (인증 토큰 포함)
     *
     * @param request 템플릿 이메일 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public EmailResult sendTemplate(EmailUtil.TemplateEmailRequest request, String accessToken) {
        String url = baseUrl + EMAIL_TEMPLATE_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", EMAIL_TEMPLATE_SEND_PATH);
            HttpEntity<EmailUtil.TemplateEmailRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            // 성공 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                Long emailId = null;
                String status = null;

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    emailId = dataMap.get("emailId") != null ? ((Number) dataMap.get("emailId")).longValue() : null;
                    status = (String) dataMap.get("status");
                }

                return EmailResult.success(emailId, status, body.getMessage());
            }

            return EmailResult.failure("TEMPLATE_EMAIL_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            // 4xx, 5xx 에러 처리
            log.error("템플릿 이메일 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpError(ex, "템플릿 이메일 발송");

        } catch (ResourceAccessException ex) {
            // 네트워크 타임아웃, 연결 실패
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return EmailResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            // 기타 RestClient 예외
            log.error("템플릿 이메일 발송 중 RestClient 예외 - url: {}", url, ex);
            return EmailResult.failure("TEMPLATE_EMAIL_SEND_ERROR", "템플릿 이메일 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            // 예상치 못한 예외
            log.error("템플릿 이메일 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return EmailResult.failure("TEMPLATE_EMAIL_SEND_ERROR", "템플릿 이메일 발송 중 오류가 발생했습니다.");
        }
    }

    // ========================================
    // 인증 이메일 발송
    // ========================================

    /**
     * 인증 이메일 발송
     *
     * @param request 인증 이메일 요청
     * @return 인증 발송 결과
     */
    public VerifyEmailResult sendVerify(EmailUtil.VerifyEmailRequest request) {
        return sendVerify(request, null);
    }

    /**
     * 인증 이메일 발송 (인증 토큰 포함)
     *
     * @param request 인증 이메일 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 인증 발송 결과
     */
    public VerifyEmailResult sendVerify(EmailUtil.VerifyEmailRequest request, String accessToken) {
        String url = baseUrl + EMAIL_VERIFY_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", EMAIL_VERIFY_SEND_PATH);
            HttpEntity<EmailUtil.VerifyEmailRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            // 성공 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;

                    Long verifyId = dataMap.get("verifyId") != null ?
                            ((Number) dataMap.get("verifyId")).longValue() : null;
                    Long emailId = dataMap.get("emailId") != null ?
                            ((Number) dataMap.get("emailId")).longValue() : null;
                    String recipient = (String) dataMap.get("recipient");
                    String verifyPurpose = (String) dataMap.get("verifyPurpose");

                    return VerifyEmailResult.success(verifyId, emailId, recipient, verifyPurpose, body.getMessage());
                }
            }

            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_FAILED", "Unexpected response: " + response.getStatusCode());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            // 4xx, 5xx 에러 처리
            log.error("인증 이메일 발송 HTTP 오류 - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpErrorForVerify(ex, "인증 이메일 발송");

        } catch (ResourceAccessException ex) {
            // 네트워크 타임아웃, 연결 실패
            log.error("이메일 서버 연결 실패 - url: {}", url, ex);
            return VerifyEmailResult.failure("EMAIL_SERVICE_UNAVAILABLE", "이메일 서버에 연결할 수 없습니다.");

        } catch (RestClientException ex) {
            // 기타 RestClient 예외
            log.error("인증 이메일 발송 중 RestClient 예외 - url: {}", url, ex);
            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_ERROR", "인증 이메일 발송 중 통신 오류: " + ex.getMessage());

        } catch (Exception ex) {
            // 예상치 못한 예외
            log.error("인증 이메일 발송 중 예상치 못한 예외 - url: {}", url, ex);
            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_ERROR", "인증 이메일 발송 중 오류가 발생했습니다.");
        }
    }

    // ========================================
    // 에러 응답 처리 헬퍼 메서드
    // ========================================

    /**
     * HTTP 에러 처리 (일반 이메일용)
     * 4xx, 5xx 에러를 통합 처리
     */
    private EmailResult handleHttpError(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "EMAIL_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return EmailResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return EmailResult.failure("EMAIL_SEND_FAILED", operation + " 실패");
        }
    }

    /**
     * HTTP 에러 처리 (인증 이메일용)
     * 4xx, 5xx 에러를 통합 처리
     */
    private VerifyEmailResult handleHttpErrorForVerify(Exception ex, String operation) {
        String responseBody = "";
        if (ex instanceof HttpClientErrorException) {
            responseBody = ((HttpClientErrorException) ex).getResponseBodyAsString();
        } else if (ex instanceof HttpServerErrorException) {
            responseBody = ((HttpServerErrorException) ex).getResponseBodyAsString();
        }

        try {
            ErrorResponse errorResponse = parseErrorResponse(responseBody);
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "VERIFY_EMAIL_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() : operation + " 실패";
            return VerifyEmailResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return VerifyEmailResult.failure("VERIFY_EMAIL_SEND_FAILED", operation + " 실패");
        }
    }

    /**
     * 에러 응답 파싱
     */
    private ErrorResponse parseErrorResponse(String responseBody) {
        try {
            return JsonUtil.fromJson(responseBody, ErrorResponse.class);
        } catch (Exception e) {
            log.debug("ErrorResponse 파싱 실패, 원본 응답: {}", responseBody);
            // 파싱 실패 시 기본 에러 응답 반환
            return ErrorResponse.builder()
                    .code("PARSE_ERROR")
                    .message(responseBody)
                    .build();
        }
    }

    // ========================================
    // 헤더 생성
    // ========================================

    /**
     * HTTP 헤더 생성
     *
     * <p>내부 서비스 간 통신을 위해 항상 새로운 서명을 생성합니다.</p>
     * <p>실제 호출할 method와 uri를 사용하여 서명을 생성합니다.</p>
     *
     * @param accessToken Bearer 토큰 (선택)
     * @param method HTTP Method (예: POST, GET)
     * @param uri 요청 URI (예: /api/v2/email/send)
     * @return HTTP 헤더
     */
    private HttpHeaders createHeaders(String accessToken, String method, String uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 내부 서비스 간 통신은 항상 새로운 서명 생성
        if (signatureValidator != null) {
            try {
                String timestamp = signatureValidator.generateTimestamp();

                // 실제 호출할 method와 uri로 서명 생성 (다른 서비스의 헤더를 복사하지 않음!)
                String signature = signatureValidator.generateSignature(method, uri, timestamp);

                headers.set("X-Gateway-Signature", signature);
                headers.set("X-Gateway-Timestamp", timestamp);

                log.debug("내부 서비스 호출용 서명 생성 완료 - method: {}, uri: {}, timestamp: {}, signature: {}...",
                        method, uri, timestamp, signature.substring(0, Math.min(10, signature.length())));
            } catch (Exception e) {
                log.error("Gateway 헤더 생성 실패", e);
            }
        } else {
            log.warn("Gateway 헤더 생성 불가 - signatureValidator가 null입니다. " +
                    "EmailClient 생성 시 GatewaySignatureValidator를 주입하세요.");
        }

        // AccessToken이 제공된 경우 Bearer 토큰 추가
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.setBearerAuth(accessToken);
        }

        return headers;
    }

    // ========================================
    // 결과 클래스 - 일반 이메일
    // ========================================

    /**
     * 이메일 발송 결과
     */
    public static class EmailResult {
        private final boolean success;
        private final Long emailId;
        private final String status;
        private final String message;
        private final String errorCode;
        private final String errorMessage;

        private EmailResult(boolean success, Long emailId, String status, String message,
                            String errorCode, String errorMessage) {
            this.success = success;
            this.emailId = emailId;
            this.status = status;
            this.message = message;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static EmailResult success(Long emailId, String status, String message) {
            return new EmailResult(true, emailId, status, message, null, null);
        }

        public static EmailResult failure(String errorCode, String errorMessage) {
            return new EmailResult(false, null, null, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getEmailId() { return emailId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("EmailResult{success=true, emailId=%d, status='%s'}", emailId, status);
            } else {
                return String.format("EmailResult{success=false, errorCode='%s', error='%s'}",
                        errorCode, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 인증 이메일
    // ========================================

    /**
     * 인증 이메일 발송 결과
     */
    public static class VerifyEmailResult {
        private final boolean success;
        private final Long verifyId;
        private final Long emailId;
        private final String recipient;
        private final String verifyPurpose;
        private final String message;
        private final String errorCode;
        private final String errorMessage;

        private VerifyEmailResult(boolean success, Long verifyId, Long emailId,
                                  String recipient, String verifyPurpose,
                                  String message, String errorCode, String errorMessage) {
            this.success = success;
            this.verifyId = verifyId;
            this.emailId = emailId;
            this.recipient = recipient;
            this.verifyPurpose = verifyPurpose;
            this.message = message;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static VerifyEmailResult success(Long verifyId, Long emailId,
                                                String recipient, String verifyPurpose,
                                                String message) {
            return new VerifyEmailResult(true, verifyId, emailId, recipient, verifyPurpose,
                    message, null, null);
        }

        public static VerifyEmailResult failure(String errorCode, String errorMessage) {
            return new VerifyEmailResult(false, null, null, null, null, null,
                    errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getVerifyId() { return verifyId; }
        public Long getEmailId() { return emailId; }
        public String getRecipient() { return recipient; }
        public String getVerifyPurpose() { return verifyPurpose; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format(
                        "VerifyEmailResult{success=true, verifyId=%d, emailId=%d, recipient='%s', purpose='%s'}",
                        verifyId, emailId, recipient, verifyPurpose
                );
            } else {
                return String.format("VerifyEmailResult{success=false, errorCode='%s', error='%s'}",
                        errorCode, errorMessage);
            }
        }
    }
}