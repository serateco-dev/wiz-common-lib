package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.softwiz.platform.iot.common.lib.dto.ApiResponse;
import org.softwiz.platform.iot.common.lib.validator.GatewaySignatureValidator;
import org.springframework.http.*;
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

            return EmailResult.failure("Unexpected response: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("이메일 발송 실패 - url: {}, error: {}", url, e.getMessage());
            return EmailResult.failure(e.getMessage());
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

            return EmailResult.failure("Unexpected response: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("템플릿 이메일 발송 실패 - url: {}, error: {}", url, e.getMessage());
            return EmailResult.failure(e.getMessage());
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

                    return VerifyEmailResult.success(
                            verifyId, emailId, recipient, verifyPurpose, body.getMessage()
                    );
                }
            }

            return VerifyEmailResult.failure("Unexpected response: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("인증 이메일 발송 실패 - url: {}, error: {}", url, e.getMessage());
            return VerifyEmailResult.failure(e.getMessage());
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

        // ✅ 내부 서비스 간 통신은 항상 새로운 서명 생성
        if (signatureValidator != null) {
            try {
                String timestamp = signatureValidator.generateTimestamp();

                // ✅ 실제 호출할 method와 uri로 서명 생성 (다른 서비스의 헤더를 복사하지 않음!)
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
        private final String errorMessage;

        private EmailResult(boolean success, Long emailId, String status, String message, String errorMessage) {
            this.success = success;
            this.emailId = emailId;
            this.status = status;
            this.message = message;
            this.errorMessage = errorMessage;
        }

        public static EmailResult success(Long emailId, String status, String message) {
            return new EmailResult(true, emailId, status, message, null);
        }

        public static EmailResult failure(String errorMessage) {
            return new EmailResult(false, null, null, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getEmailId() { return emailId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("EmailResult{success=true, emailId=%d, status='%s'}", emailId, status);
            } else {
                return String.format("EmailResult{success=false, error='%s'}", errorMessage);
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
        private final String errorMessage;

        private VerifyEmailResult(boolean success, Long verifyId, Long emailId, 
                                  String recipient, String verifyPurpose, 
                                  String message, String errorMessage) {
            this.success = success;
            this.verifyId = verifyId;
            this.emailId = emailId;
            this.recipient = recipient;
            this.verifyPurpose = verifyPurpose;
            this.message = message;
            this.errorMessage = errorMessage;
        }

        public static VerifyEmailResult success(Long verifyId, Long emailId, 
                                                String recipient, String verifyPurpose, 
                                                String message) {
            return new VerifyEmailResult(true, verifyId, emailId, recipient, verifyPurpose, message, null);
        }

        public static VerifyEmailResult failure(String errorMessage) {
            return new VerifyEmailResult(false, null, null, null, null, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getVerifyId() { return verifyId; }
        public Long getEmailId() { return emailId; }
        public String getRecipient() { return recipient; }
        public String getVerifyPurpose() { return verifyPurpose; }
        public String getMessage() { return message; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format(
                        "VerifyEmailResult{success=true, verifyId=%d, emailId=%d, recipient='%s', purpose='%s'}",
                        verifyId, emailId, recipient, verifyPurpose
                );
            } else {
                return String.format("VerifyEmailResult{success=false, error='%s'}", errorMessage);
            }
        }
    }
}