package org.softwiz.platform.iot.common.lib.interceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.softwiz.platform.iot.common.lib.context.GatewayContext;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.softwiz.platform.iot.common.lib.util.CryptoUtil;
import org.softwiz.platform.iot.common.lib.validator.GatewaySignatureValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GatewayHeaderInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GatewayHeaderInterceptor.class);

    private final CryptoUtil cryptoUtil;
    private final GatewaySignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    @Value("${gateway.signature.enabled:true}")
    private boolean signatureEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

        String encryptedUserId = request.getHeader("X-User-Id");

        if (encryptedUserId == null || encryptedUserId.isBlank()) {
            // 익명 요청: 서명 검증 필수
            if (signatureEnabled && !signatureValidator.validateSignature(request)) {
                log.warn("Gateway signature validation failed: {} {}", request.getMethod(), request.getRequestURI());
                sendUnauthorizedResponse(response, "Invalid gateway signature");
                return false;
            }

            // 익명 요청은 통과 (Kafka 로그 수집용)
            return true;
        }

        if (encryptedUserId != null && !encryptedUserId.isBlank()) {
            // 기존 헤더 추출
            String userNoHeader = request.getHeader("X-User-No");
            String serviceId = request.getHeader("X-Service-Id");
            String role = request.getHeader("X-Role");
            String authHeader = request.getHeader("X-Auth");
            String provider = request.getHeader("X-Provider");
            String nickName = request.getHeader("X-Nick-Name");
            String clientIp = request.getHeader("X-Client-Ip");
            String deviceCd = request.getHeader("X-Device-Cd");
            String deviceStr = request.getHeader("X-Device-Str");

            // ✅ Authorization 헤더 추출
            String authorizationHeader = request.getHeader("Authorization");
            String accessToken = null;
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                accessToken = authorizationHeader.substring(7);
                log.debug("🎫 AccessToken extracted from Authorization header");
            }

            Long userNo = parseUserNo(userNoHeader);

            // userId 복호화
            String decryptedUserId;
            try {
                decryptedUserId = cryptoUtil.decrypt(encryptedUserId);
            } catch (Exception e) {
                log.error("Failed to decrypt userId: {}", e.getMessage());
                sendUnauthorizedResponse(response, "인증실패");
                return false;
            }

            List<String> auth = parseAuthHeader(authHeader);

            // MDC 설정
            if (serviceId != null && !serviceId.isBlank()) {
                MDC.put("serviceId", serviceId);
            }
            if (nickName != null && !nickName.isBlank()) {
                MDC.put("nickName", nickName);
            }

            // ✅ GatewayContext에 accessToken 포함
            GatewayContext context = GatewayContext.builder()
                    .userNo(userNo)
                    .userId(decryptedUserId)
                    .serviceId(serviceId)
                    .role(role)
                    .auth(auth)
                    .provider(provider)
                    .nickName(nickName)
                    .clientIp(clientIp)
                    .deviceCd(deviceCd)
                    .deviceStr(deviceStr)
                    .accessToken(accessToken)  // ✅ 추가
                    .build();

            GatewayContext.setContext(context);

            if (log.isDebugEnabled()) {
                log.debug("Gateway context initialized - UserNo: {}, Service: {}, Role: {}, HasToken: {}",
                        userNo, serviceId, role, accessToken != null);
            }

            return true;

        } else {
            log.warn("Missing X-User-Id header: {} {}", request.getMethod(), request.getRequestURI());
            sendUnauthorizedResponse(response, "인증실패");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove("serviceId");
        MDC.remove("nickName");
        GatewayContext.clear();
    }

    private Long parseUserNo(String userNoHeader) {
        if (userNoHeader != null && !userNoHeader.isBlank()) {
            try {
                return Long.parseLong(userNoHeader);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse userNo header: {}", userNoHeader);
                return null;
            }
        }
        return null;
    }

    private List<String> parseAuthHeader(String authHeader) {
        if (authHeader != null && !authHeader.isBlank()) {
            try {
                List<String> auth = objectMapper.readValue(authHeader, new TypeReference<List<String>>() {});
                return auth != null ? auth : List.of();
            } catch (Exception e) {
                log.warn("Failed to parse auth header: {}", e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("Unauthorized")
                .message(message)
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}