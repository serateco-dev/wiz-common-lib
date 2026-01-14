package org.softwiz.platform.iot.common.lib.util;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 푸시 서비스 클라이언트
 *
 * <p>다른 마이크로서비스에서 WizMessage 푸시 서비스를 호출할 때 사용합니다.</p>
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
 * public class PushClientConfig {
 *     @Value("${microservice.message.url:http://wizmessage:8098}")
 *     private String messageServiceUrl;
 *
 *     @Bean
 *     public PushClient pushClient(RestTemplate restTemplate,
 *                                   GatewaySignatureValidator signatureValidator) {
 *         return new PushClient(restTemplate, messageServiceUrl, signatureValidator);
 *     }
 * }
 * }</pre>
 */
@Slf4j
public class PushClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final GatewaySignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    private static final String PUSH_SEND_PATH = "/api/v2/push/send";
    private static final String TOKEN_SAVE_PATH = "/api/v2/push/token/save";
    private static final String TEMPLATE_SEND_PATH = "/api/v2/push/template/send";

    /**
     * 생성자 (GatewaySignatureValidator 포함)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 푸시 서비스 기본 URL (예: http://wizmessage:8098)
     * @param signatureValidator Gateway 서명 검증/생성기
     */
    public PushClient(RestTemplate restTemplate, String baseUrl, GatewaySignatureValidator signatureValidator) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signatureValidator = signatureValidator;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 생성자 (하위 호환성 유지)
     *
     * @param restTemplate RestTemplate 인스턴스
     * @param baseUrl 푸시 서비스 기본 URL
     * @deprecated signatureValidator를 포함하는 생성자 사용 권장
     */
    @Deprecated
    public PushClient(RestTemplate restTemplate, String baseUrl) {
        this(restTemplate, baseUrl, null);
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
            HttpHeaders headers = createHeaders(accessToken, "POST", PUSH_SEND_PATH);
            HttpEntity<PushUtil.PushRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            // ✅ 2xx 성공 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
                Object data = body.getData();

                Long pushId = null;
                String status = null;

                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    pushId = dataMap.get("pushId") != null ? ((Number) dataMap.get("pushId")).longValue() : null;
                    status = (String) dataMap.get("status");
                }

                return PushResult.success(pushId, status, body.getMessage());
            }

            // ❌ 2xx가 아닌 응답
            return PushResult.failure(
                    "PUSH_SEND_FAILED",
                    "푸시 서버 응답 오류: " + response.getStatusCode()
            );

        } catch (HttpClientErrorException ex) {
            // ✅ 4xx 클라이언트 에러 처리
            log.warn("푸시 발송 클라이언트 오류 - Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpClientError(ex, "푸시 발송");

        } catch (HttpServerErrorException ex) {
            // ✅ 5xx 서버 에러 처리
            log.error("푸시 발송 서버 오류 - Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpServerError(ex, "푸시 발송");

        } catch (ResourceAccessException ex) {
            // ✅ 네트워크 타임아웃, 연결 실패
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return PushResult.failure(
                    "PUSH_SERVICE_UNAVAILABLE",
                    "푸시 서버에 연결할 수 없습니다. 네트워크를 확인해주세요."
            );

        } catch (RestClientException ex) {
            // ✅ 기타 RestClient 예외
            log.error("푸시 발송 중 RestClient 예외 - url: {}", url, ex);
            return PushResult.failure(
                    "PUSH_SEND_ERROR",
                    "푸시 발송 중 통신 오류가 발생했습니다: " + ex.getMessage()
            );

        } catch (Exception ex) {
            // ✅ 예상치 못한 예외
            log.error("푸시 발송 중 예상치 못한 예외 발생 - url: {}", url, ex);
            return PushResult.failure(
                    "PUSH_SEND_ERROR",
                    "푸시 발송 중 오류가 발생했습니다"
            );
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
            HttpHeaders headers = createHeaders(accessToken, "POST", TEMPLATE_SEND_PATH);
            HttpEntity<PushUtil.TemplatePushRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            // ✅ 2xx 성공 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ApiResponse body = response.getBody();
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

                    List<PushResultItem> results = parseResults(dataMap.get("results"));

                    Long pushId = null;
                    String status = null;
                    if (!results.isEmpty()) {
                        PushResultItem firstItem = results.get(0);
                        pushId = firstItem.getPushId();
                        status = firstItem.getStatus();
                    }

                    return TemplatePushResult.success(
                            templateCode, resolvedTitle, resolvedContent,
                            totalCount, successCount, failedCount, skippedCount,
                            pushId, status, results, body.getMessage()
                    );
                }

                return TemplatePushResult.success(
                        null, null, null, 0, 0, 0, 0,
                        null, null, Collections.emptyList(), body.getMessage()
                );
            }

            // ❌ 2xx가 아닌 응답
            return TemplatePushResult.failure(
                    "TEMPLATE_PUSH_SEND_FAILED",
                    "푸시 서버 응답 오류: " + response.getStatusCode()
            );

        } catch (HttpClientErrorException ex) {
            // ✅ 4xx 클라이언트 에러 처리
            log.warn("템플릿 푸시 발송 클라이언트 오류 - Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpClientErrorForTemplate(ex, "템플릿 푸시 발송");

        } catch (HttpServerErrorException ex) {
            // ✅ 5xx 서버 에러 처리
            log.error("템플릿 푸시 발송 서버 오류 - Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpServerErrorForTemplate(ex, "템플릿 푸시 발송");

        } catch (ResourceAccessException ex) {
            // ✅ 네트워크 타임아웃, 연결 실패
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return TemplatePushResult.failure(
                    "PUSH_SERVICE_UNAVAILABLE",
                    "푸시 서버에 연결할 수 없습니다. 네트워크를 확인해주세요."
            );

        } catch (RestClientException ex) {
            // ✅ 기타 RestClient 예외
            log.error("템플릿 푸시 발송 중 RestClient 예외 - url: {}", url, ex);
            return TemplatePushResult.failure(
                    "TEMPLATE_PUSH_SEND_ERROR",
                    "템플릿 푸시 발송 중 통신 오류가 발생했습니다: " + ex.getMessage()
            );

        } catch (Exception ex) {
            // ✅ 예상치 못한 예외
            log.error("템플릿 푸시 발송 중 예상치 못한 예외 발생 - url: {}", url, ex);
            return TemplatePushResult.failure(
                    "TEMPLATE_PUSH_SEND_ERROR",
                    "템플릿 푸시 발송 중 오류가 발생했습니다"
            );
        }
    }

    /**
     * results 배열 파싱
     */
    @SuppressWarnings("unchecked")
    private List<PushResultItem> parseResults(Object resultsObj) {
        if (resultsObj == null) {
            return Collections.emptyList();
        }

        if (!(resultsObj instanceof List)) {
            return Collections.emptyList();
        }

        List<?> resultsList = (List<?>) resultsObj;
        List<PushResultItem> items = new ArrayList<>();

        for (Object item : resultsList) {
            if (item instanceof java.util.Map) {
                java.util.Map<?, ?> itemMap = (java.util.Map<?, ?>) item;

                Long pushId = itemMap.get("pushId") != null ?
                        ((Number) itemMap.get("pushId")).longValue() : null;
                Long userNo = itemMap.get("userNo") != null ?
                        ((Number) itemMap.get("userNo")).longValue() : null;
                String status = (String) itemMap.get("status");
                String errorMessage = (String) itemMap.get("errorMessage");

                items.add(new PushResultItem(pushId, userNo, status, errorMessage));
            }
        }

        return items;
    }

    // ========================================
    // 토큰 저장
    // ========================================

    /**
     * 토큰 저장
     *
     * @param request 토큰 저장 요청
     * @return 저장 결과
     */
    public TokenSaveResult saveToken(PushUtil.TokenRequest request) {
        return saveToken(request, null);
    }

    /**
     * 토큰 저장 (인증 토큰 포함)
     *
     * @param request 토큰 저장 요청
     * @param accessToken 접근 토큰
     * @return 저장 결과
     */
    public TokenSaveResult saveToken(PushUtil.TokenRequest request, String accessToken) {
        String url = baseUrl + TOKEN_SAVE_PATH;

        try {
            HttpHeaders headers = createHeaders(accessToken, "POST", TOKEN_SAVE_PATH);
            HttpEntity<PushUtil.TokenRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ApiResponse.class
            );

            // ✅ 2xx 성공 응답 처리
            if (response.getStatusCode().is2xxSuccessful()) {
                return TokenSaveResult.success("토큰이 성공적으로 저장되었습니다");
            }

            // ❌ 2xx가 아닌 응답
            return TokenSaveResult.failure(
                    "TOKEN_SAVE_FAILED",
                    "푸시 서버 응답 오류: " + response.getStatusCode()
            );

        } catch (HttpClientErrorException ex) {
            // ✅ 4xx 클라이언트 에러 처리
            log.warn("토큰 저장 클라이언트 오류 - Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpClientErrorForToken(ex, "토큰 저장");

        } catch (HttpServerErrorException ex) {
            // ✅ 5xx 서버 에러 처리
            log.error("토큰 저장 서버 오류 - Status: {}, Body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return handleHttpServerErrorForToken(ex, "토큰 저장");

        } catch (ResourceAccessException ex) {
            // ✅ 네트워크 타임아웃, 연결 실패
            log.error("푸시 서버 연결 실패 - url: {}", url, ex);
            return TokenSaveResult.failure(
                    "PUSH_SERVICE_UNAVAILABLE",
                    "푸시 서버에 연결할 수 없습니다. 네트워크를 확인해주세요."
            );

        } catch (RestClientException ex) {
            // ✅ 기타 RestClient 예외
            log.error("토큰 저장 중 RestClient 예외 - url: {}", url, ex);
            return TokenSaveResult.failure(
                    "TOKEN_SAVE_ERROR",
                    "토큰 저장 중 통신 오류가 발생했습니다: " + ex.getMessage()
            );

        } catch (Exception ex) {
            // ✅ 예상치 못한 예외
            log.error("토큰 저장 중 예상치 못한 예외 발생 - url: {}", url, ex);
            return TokenSaveResult.failure(
                    "TOKEN_SAVE_ERROR",
                    "토큰 저장 중 오류가 발생했습니다"
            );
        }
    }

    // ========================================
    // 에러 응답 처리 헬퍼 메서드
    // ========================================

    /**
     * HTTP 4xx 클라이언트 에러 처리 (일반 푸시용)
     */
    private PushResult handleHttpClientError(HttpClientErrorException ex, String operation) {
        try {
            ErrorResponse errorResponse = parseErrorResponse(ex.getResponseBodyAsString());
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "PUSH_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() :
                    operation + " 실패: " + ex.getStatusCode();

            return PushResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return PushResult.failure(
                    "PUSH_SEND_FAILED",
                    operation + " 실패: " + ex.getStatusCode()
            );
        }
    }

    /**
     * HTTP 5xx 서버 에러 처리 (일반 푸시용)
     */
    private PushResult handleHttpServerError(HttpServerErrorException ex, String operation) {
        try {
            ErrorResponse errorResponse = parseErrorResponse(ex.getResponseBodyAsString());
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "PUSH_SEND_ERROR";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() :
                    operation + " 중 서버 오류 발생: " + ex.getStatusCode();

            return PushResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return PushResult.failure(
                    "PUSH_SEND_ERROR",
                    operation + " 중 서버 오류 발생: " + ex.getStatusCode()
            );
        }
    }

    /**
     * HTTP 4xx 클라이언트 에러 처리 (템플릿 푸시용)
     */
    private TemplatePushResult handleHttpClientErrorForTemplate(HttpClientErrorException ex, String operation) {
        try {
            ErrorResponse errorResponse = parseErrorResponse(ex.getResponseBodyAsString());
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "TEMPLATE_PUSH_SEND_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() :
                    operation + " 실패: " + ex.getStatusCode();

            return TemplatePushResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return TemplatePushResult.failure(
                    "TEMPLATE_PUSH_SEND_FAILED",
                    operation + " 실패: " + ex.getStatusCode()
            );
        }
    }

    /**
     * HTTP 5xx 서버 에러 처리 (템플릿 푸시용)
     */
    private TemplatePushResult handleHttpServerErrorForTemplate(HttpServerErrorException ex, String operation) {
        try {
            ErrorResponse errorResponse = parseErrorResponse(ex.getResponseBodyAsString());
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "TEMPLATE_PUSH_SEND_ERROR";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() :
                    operation + " 중 서버 오류 발생: " + ex.getStatusCode();

            return TemplatePushResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return TemplatePushResult.failure(
                    "TEMPLATE_PUSH_SEND_ERROR",
                    operation + " 중 서버 오류 발생: " + ex.getStatusCode()
            );
        }
    }

    /**
     * HTTP 4xx 클라이언트 에러 처리 (토큰 저장용)
     */
    private TokenSaveResult handleHttpClientErrorForToken(HttpClientErrorException ex, String operation) {
        try {
            ErrorResponse errorResponse = parseErrorResponse(ex.getResponseBodyAsString());
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "TOKEN_SAVE_FAILED";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() :
                    operation + " 실패: " + ex.getStatusCode();

            return TokenSaveResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return TokenSaveResult.failure(
                    "TOKEN_SAVE_FAILED",
                    operation + " 실패: " + ex.getStatusCode()
            );
        }
    }

    /**
     * HTTP 5xx 서버 에러 처리 (토큰 저장용)
     */
    private TokenSaveResult handleHttpServerErrorForToken(HttpServerErrorException ex, String operation) {
        try {
            ErrorResponse errorResponse = parseErrorResponse(ex.getResponseBodyAsString());
            String code = errorResponse.getCode() != null ? errorResponse.getCode() : "TOKEN_SAVE_ERROR";
            String message = errorResponse.getMessage() != null ? errorResponse.getMessage() :
                    operation + " 중 서버 오류 발생: " + ex.getStatusCode();

            return TokenSaveResult.failure(code, message);
        } catch (Exception parseEx) {
            log.warn("에러 응답 파싱 실패", parseEx);
            return TokenSaveResult.failure(
                    "TOKEN_SAVE_ERROR",
                    operation + " 중 서버 오류 발생: " + ex.getStatusCode()
            );
        }
    }

    /**
     * 에러 응답 파싱
     */
    private ErrorResponse parseErrorResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, ErrorResponse.class);
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
     * @param uri 요청 URI (예: /api/v2/push/send)
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
                    "PushClient 생성 시 GatewaySignatureValidator를 주입하세요.");
        }

        // AccessToken이 제공된 경우 Bearer 토큰 추가
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
        private final String errorCode;
        private final String errorMessage;

        private PushResult(boolean success, Long pushId, String status, String message,
                           String errorCode, String errorMessage) {
            this.success = success;
            this.pushId = pushId;
            this.status = status;
            this.message = message;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static PushResult success(Long pushId, String status, String message) {
            return new PushResult(true, pushId, status, message, null, null);
        }

        public static PushResult failure(String errorCode, String errorMessage) {
            return new PushResult(false, null, null, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getPushId() { return pushId; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("PushResult{success=true, pushId=%d, status='%s'}", pushId, status);
            } else {
                return String.format("PushResult{success=false, errorCode='%s', error='%s'}",
                        errorCode, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 개별 푸시 결과 항목
    // ========================================

    /**
     * 개별 푸시 발송 결과 항목 (다중 발송 시 사용)
     */
    public static class PushResultItem {
        private final Long pushId;
        private final Long userNo;
        private final String status;
        private final String errorMessage;

        public PushResultItem(Long pushId, Long userNo, String status, String errorMessage) {
            this.pushId = pushId;
            this.userNo = userNo;
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public Long getPushId() { return pushId; }
        public Long getUserNo() { return userNo; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("PushResultItem{pushId=%d, userNo=%d, status='%s'}",
                    pushId, userNo, status);
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
        private final Long pushId;
        private final String status;
        private final List<PushResultItem> results;
        private final String message;
        private final String errorCode;
        private final String errorMessage;

        private TemplatePushResult(boolean success, String templateCode, String resolvedTitle,
                                   String resolvedContent, int totalCount, int successCount,
                                   int failedCount, int skippedCount, Long pushId, String status,
                                   List<PushResultItem> results, String message,
                                   String errorCode, String errorMessage) {
            this.success = success;
            this.templateCode = templateCode;
            this.resolvedTitle = resolvedTitle;
            this.resolvedContent = resolvedContent;
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
            this.pushId = pushId;
            this.status = status;
            this.results = results != null ? results : Collections.emptyList();
            this.message = message;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static TemplatePushResult success(String templateCode, String resolvedTitle,
                                                 String resolvedContent, int totalCount,
                                                 int successCount, int failedCount, int skippedCount,
                                                 Long pushId, String status,
                                                 List<PushResultItem> results, String message) {
            return new TemplatePushResult(true, templateCode, resolvedTitle, resolvedContent,
                    totalCount, successCount, failedCount, skippedCount, pushId, status,
                    results, message, null, null);
        }

        public static TemplatePushResult failure(String errorCode, String errorMessage) {
            return new TemplatePushResult(false, null, null, null, 0, 0, 0, 0,
                    null, null, Collections.emptyList(), null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getTemplateCode() { return templateCode; }
        public String getResolvedTitle() { return resolvedTitle; }
        public String getResolvedContent() { return resolvedContent; }
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailedCount() { return failedCount; }
        public int getSkippedCount() { return skippedCount; }
        public Long getPushId() { return pushId; }
        public String getStatus() { return status; }
        public List<PushResultItem> getResults() { return results; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format(
                        "TemplatePushResult{success=true, template='%s', pushId=%d, status='%s', total=%d, success=%d, failed=%d, skipped=%d}",
                        templateCode, pushId, status, totalCount, successCount, failedCount, skippedCount
                );
            } else {
                return String.format("TemplatePushResult{success=false, errorCode='%s', error='%s'}",
                        errorCode, errorMessage);
            }
        }
    }

    // ========================================
    // 결과 클래스 - 토큰 저장
    // ========================================

    /**
     * 토큰 저장 결과
     */
    public static class TokenSaveResult {
        private final boolean success;
        private final String message;
        private final String errorCode;
        private final String errorMessage;

        private TokenSaveResult(boolean success, String message, String errorCode, String errorMessage) {
            this.success = success;
            this.message = message;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static TokenSaveResult success(String message) {
            return new TokenSaveResult(true, message, null, null);
        }

        public static TokenSaveResult failure(String errorCode, String errorMessage) {
            return new TokenSaveResult(false, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("TokenSaveResult{success=true, message='%s'}", message);
            } else {
                return String.format("TokenSaveResult{success=false, errorCode='%s', error='%s'}",
                        errorCode, errorMessage);
            }
        }
    }
}