package org.softwiz.platform.iot.common.lib.validator;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gateway 서명 검증기
 *
 * Gateway에서 전송한 서명을 검증하여 직접 호출을 방지합니다.
 * Mock 환경에서는 특정 Mock 시그니처를 허용합니다.
 *
 * 보안: signatureSecret은 반드시 환경변수 또는 application-{profile}.yml에서 설정해야 합니다.
 * 기본값이 없으므로 운영 환경에서 설정하지 않으면 애플리케이션 시작 시 에러가 발생합니다.
 */
@Slf4j
@Component
public class GatewaySignatureValidator {

    @Value("${gateway.signature.secret}")
    private String signatureSecret;

    @Value("${gateway.signature.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${gateway.signature.enabled:true}")
    private boolean signatureEnabled;

    private static final String MOCK_SIGNATURE = "MOCK_GATEWAY_SIGNATURE_FOR_TESTING";

    /**
     * Gateway 서명 검증
     *
     * @param request HTTP 요청
     * @return 서명이 유효하면 true, 그렇지 않으면 false
     */
    public boolean validateSignature(HttpServletRequest request) {
        // 서명 검증이 비활성화된 경우 (개발 편의용)
        if (!signatureEnabled) {
            log.warn("Gateway signature validation is DISABLED");
            return true;
        }

        String signature = request.getHeader("X-Gateway-Signature");
        String timestamp = request.getHeader("X-Gateway-Timestamp");

        // 1. Mock 시그니처 체크 (로컬 테스트용)
        if (mockEnabled && MOCK_SIGNATURE.equals(signature)) {
            log.debug("Mock signature accepted (mock-enabled=true)");
            return true;
        }

        // 2. 헤더 존재 여부 확인
        if (signature == null || signature.isBlank()) {
            log.warn("Missing X-Gateway-Signature header");
            return false;
        }

        if (timestamp == null || timestamp.isBlank()) {
            log.warn("Missing X-Gateway-Timestamp header");
            return false;
        }

        // 3. 타임스탬프 유효성 확인 (5분 이내)
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis();
            long timeDiff = Math.abs(currentTime - requestTime);

            if (timeDiff > 300000) { // 5분 = 300,000ms
                log.warn("Timestamp expired - Diff: {}ms", timeDiff);
                return false;
            }
        } catch (NumberFormatException e) {
            log.error("Invalid timestamp format: {}", timestamp);
            return false;
        }

        // 4. 전체 URI 구성 (쿼리 파라미터 포함)
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUri = queryString != null ? requestUri + "?" + queryString : requestUri;
        String method = request.getMethod();

        // 5. 서명 검증
        String expectedSignature = generateSignature(method, fullUri, timestamp);
        boolean isValid = expectedSignature.equals(signature);

        if (isValid) {
            log.trace("Gateway signature validated successfully");
        } else {
            log.warn("Gateway signature mismatch - Method: {}, URI: {}", method, fullUri);

            if (log.isDebugEnabled()) {
                log.debug("Expected signature: {}", expectedSignature);
                log.debug("Actual signature: {}", signature);

                String gatewayUri = request.getHeader("X-Gateway-Request-URI");
                if (gatewayUri != null && !gatewayUri.equals(fullUri)) {
                    log.debug("URI mismatch - Gateway: '{}', SSO: '{}'", gatewayUri, fullUri);
                }
            }
        }

        return isValid;
    }

    /**
     * 서명 생성 (HMAC-SHA256)
     *
     * @param method HTTP 메서드
     * @param uri 요청 URI (쿼리 파라미터 포함)
     * @param timestamp 타임스탬프
     * @return Base64 인코딩된 서명
     */
    private String generateSignature(String method, String uri, String timestamp) {
        try {
            String data = String.format("%s:%s:%s", method, uri, timestamp);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    signatureSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacData);

        } catch (Exception e) {
            log.error("Failed to generate signature", e);
            return "";
        }
    }

    /**
     * Mock 시그니처 생성 (DevController에서 사용)
     *
     * @param method HTTP 메서드
     * @param uri 요청 URI (쿼리 파라미터 포함)
     * @return Mock 환경용 시그니처
     */
    public String generateMockSignature(String method, String uri) {
        if (mockEnabled) {
            return MOCK_SIGNATURE;
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        return generateSignature(method, uri, timestamp);
    }

    /**
     * Mock 타임스탬프 생성
     *
     * @return 현재 시간의 타임스탬프
     */
    public String generateMockTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }
}