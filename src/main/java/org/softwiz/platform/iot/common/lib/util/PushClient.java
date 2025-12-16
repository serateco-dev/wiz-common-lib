package org.softwiz.platform.iot.common.lib.util;

import lombok.extern.slf4j.Slf4j;
import org.softwiz.platform.iot.common.lib.dto.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 푸시 서비스 클라이언트
 *
 * <p>다른 마이크로서비스에서 WizMessage 푸시 서비스를 호출할 때 사용합니다.</p>
 *
 * <pre>
 * 사용 예시 (Spring Bean으로 등록):
 * {@code
 * @Configuration
 * public class PushClientConfig {
 *     @Value("${microservice.message.url:http://wizmessage:8095}")
 *     private String messageServiceUrl;
 *
 *     @Bean
 *     public PushClient pushClient(RestTemplate restTemplate) {
 *         return new PushClient(restTemplate, messageServiceUrl);
 *     }
 * }
 * }
 * </pre>
 *
 * <pre>
 * 사용 예시 (서비스에서 호출):
 * {@code
 * @Service
 * @RequiredArgsConstructor
 * public class NotificationService {
 *     private final PushClient pushClient;
 *
 *     // 일반 푸시
 *     public void notifyUser(Long userNo, String message) {
 *         PushUtil.PushRequest request = PushUtil.builder()
 *             .serviceId("NEST")
 *             .userNo(userNo)
 *             .content(message)
 *             .build();
 *
 *         PushClient.PushResult result = pushClient.send(request);
 *         if (result.isSuccess()) {
 *             log.info("푸시 발송 성공: {}", result.getPushId());
 *         }
 *     }
 *
 *     // 템플릿 푸시
 *     public void notifyOrderComplete(Long userNo, String orderNo) {
 *         PushUtil.TemplatePushRequest request = PushUtil.templateBuilder()
 *             .serviceId("NEST")
 *             .templateCode("ORDER_COMPLETE")
 *             .userNo(userNo)
 *             .variable("orderNo", orderNo)
 *             .build();
 *
 *         PushClient.TemplatePushResult result = pushClient.sendTemplate(request);
 *         if (result.isSuccess()) {
 *             log.info("템플릿 푸시 발송 성공: {}", result.getResolvedTitle());
 *         }
 *     }
 * }
 * }
 * </pre>
 */
@Slf4j
public class PushClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    private static final String PUSH_SEND_PATH = "/api/v2/push/send";
    private static final String TOKEN_SAVE_PATH = "/api/v2/push/token/save";
    private static final String TEMPLATE_SEND_PATH = "/api/v2/push/template/send";

    /**
     * 생성자
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 푸시 서비스 기본 URL (예: http://wizmessage:8095)
     */
    public PushClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // ========================================
    // 일반 푸시 발송
    // ========================================

    /**
     * 푸시 발송
     *
     * @param request 푸시 요청
     * @return 발송 결과
     */
    public PushResult send(PushUtil.PushRequest request) {
        return send(request, null);
    }

    /**
     * 푸시 발송 (인증 토큰 포함)
     *
     * @param request 푸시 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public PushResult send(PushUtil.PushRequest request, String accessToken) {
        String url = baseUrl + PUSH_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<PushUtil.PushRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse<?> body = response.getBody();
                Object data = body.getData();

                Long pushId = null;
                String status = null;

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    pushId = dataMap.get("pushId") != null ?
                            ((Number) dataMap.get("pushId")).longValue() : null;
                    status = (String) dataMap.get("status");
                }

                return PushResult.success(pushId, status, body.getMessage());
            }

            return PushResult.failure("Unexpected response: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("푸시 발송 실패 - url: {}, error: {}", url, e.getMessage());
            return PushResult.failure(e.getMessage());
        }
    }

    // ========================================
    // 템플릿 푸시 발송
    // ========================================

    /**
     * 템플릿 기반 푸시 발송
     *
     * @param request 템플릿 푸시 요청
     * @return 발송 결과
     */
    public TemplatePushResult sendTemplate(PushUtil.TemplatePushRequest request) {
        return sendTemplate(request, null);
    }

    /**
     * 템플릿 기반 푸시 발송 (인증 토큰 포함)
     *
     * @param request 템플릿 푸시 요청
     * @param accessToken 접근 토큰 (Gateway 인증용)
     * @return 발송 결과
     */
    public TemplatePushResult sendTemplate(PushUtil.TemplatePushRequest request, String accessToken) {
        String url = baseUrl + TEMPLATE_SEND_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<PushUtil.TemplatePushRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse<?> body = response.getBody();
                Object data = body.getData();

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;

                    String templateCode = (String) dataMap.get("templateCode");
                    String resolvedTitle = (String) dataMap.get("resolvedTitle");
                    String resolvedContent = (String) dataMap.get("resolvedContent");
                    Integer totalCount = dataMap.get("totalCount") != null ?
                            ((Number) dataMap.get("totalCount")).intValue() : 0;
                    Integer successCount = dataMap.get("successCount") != null ?
                            ((Number) dataMap.get("successCount")).intValue() : 0;
                    Integer failedCount = dataMap.get("failedCount") != null ?
                            ((Number) dataMap.get("failedCount")).intValue() : 0;
                    Integer skippedCount = dataMap.get("skippedCount") != null ?
                            ((Number) dataMap.get("skippedCount")).intValue() : 0;

                    return TemplatePushResult.success(
                            templateCode, resolvedTitle, resolvedContent,
                            totalCount, successCount, failedCount, skippedCount,
                            body.getMessage()
                    );
                }

                return TemplatePushResult.success(null, null, null, 0, 0, 0, 0, body.getMessage());
            }

            return TemplatePushResult.failure("Unexpected response: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("템플릿 푸시 발송 실패 - url: {}, error: {}", url, e.getMessage());
            return TemplatePushResult.failure(e.getMessage());
        }
    }

    // ========================================
    // 토큰 저장
    // ========================================

    /**
     * 토큰 저장
     *
     * @param request 토큰 저장 요청
     * @return 저장 성공 여부
     */
    public boolean saveToken(PushUtil.TokenRequest request) {
        return saveToken(request, null);
    }

    /**
     * 토큰 저장 (인증 토큰 포함)
     *
     * @param request 토큰 저장 요청
     * @param accessToken 접근 토큰
     * @return 저장 성공 여부
     */
    public boolean saveToken(PushUtil.TokenRequest request, String accessToken) {
        String url = baseUrl + TOKEN_SAVE_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<PushUtil.TokenRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (RestClientException e) {
            log.error("토큰 저장 실패 - url: {}, error: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * HTTP 헤더 생성
     */
    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (accessToken != null && !accessToken.isEmpty()) {
            headers.setBearerAuth(accessToken);
        }

        return headers;
    }

    // ========================================
    // 결과 클래스 - 일반 푸시
    // ========================================

    /**
     * 푸시 발송 결과
     */
    public static class PushResult {
        private final boolean success;
        private final Long pushId;
        private final String status;
        private final String message;
        private final String errorMessage;

        private PushResult(boolean success, Long pushId, String status, String message, String errorMessage) {
            this.success = success;
            this.pushId = pushId;
            this.status = status;
            this.message = message;
            this.errorMessage = errorMessage;
        }

        public static PushResult success(Long pushId, String status, String message) {
            return new PushResult(true, pushId, status, message, null);
        }

        public static PushResult failure(String errorMessage) {
            return new PushResult(false, null, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public Long getPushId() {
            return pushId;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("PushResult{success=true, pushId=%d, status='%s'}", pushId, status);
            } else {
                return String.format("PushResult{success=false, error='%s'}", errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 템플릿 푸시
    // ========================================

    /**
     * 템플릿 푸시 발송 결과
     */
    public static class TemplatePushResult {
        private final boolean success;
        private final String templateCode;
        private final String resolvedTitle;
        private final String resolvedContent;
        private final int totalCount;
        private final int successCount;
        private final int failedCount;
        private final int skippedCount;
        private final String message;
        private final String errorMessage;

        private TemplatePushResult(boolean success, String templateCode,
                                   String resolvedTitle, String resolvedContent,
                                   int totalCount, int successCount, int failedCount, int skippedCount,
                                   String message, String errorMessage) {
            this.success = success;
            this.templateCode = templateCode;
            this.resolvedTitle = resolvedTitle;
            this.resolvedContent = resolvedContent;
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
            this.message = message;
            this.errorMessage = errorMessage;
        }

        public static TemplatePushResult success(String templateCode, String resolvedTitle, String resolvedContent,
                                                 int totalCount, int successCount, int failedCount, int skippedCount,
                                                 String message) {
            return new TemplatePushResult(true, templateCode, resolvedTitle, resolvedContent,
                    totalCount, successCount, failedCount, skippedCount, message, null);
        }

        public static TemplatePushResult failure(String errorMessage) {
            return new TemplatePushResult(false, null, null, null, 0, 0, 0, 0, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getTemplateCode() { return templateCode; }
        public String getResolvedTitle() { return resolvedTitle; }
        public String getResolvedContent() { return resolvedContent; }
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailedCount() { return failedCount; }
        public int getSkippedCount() { return skippedCount; }
        public String getMessage() { return message; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("TemplatePushResult{success=true, template='%s', total=%d, success=%d, failed=%d, skipped=%d}",
                        templateCode, totalCount, successCount, failedCount, skippedCount);
            } else {
                return String.format("TemplatePushResult{success=false, error='%s'}", errorMessage);
            }
        }
    }
}