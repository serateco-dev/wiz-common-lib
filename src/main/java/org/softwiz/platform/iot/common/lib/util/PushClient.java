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
    // 결과 클래스
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
}
