package org.softwiz.platform.iot.common.lib.interceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import org.softwiz.platform.iot.common.lib.context.GatewayContext;
import org.softwiz.platform.iot.common.lib.dto.ErrorResponse;
import org.softwiz.platform.iot.common.lib.util.CryptoUtil;
import org.softwiz.platform.iot.common.lib.validator.GatewaySignatureValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * Gateway Header Interceptor
 *
 * Gateway에서 전달된 헤더를 검증하고 컨텍스트를 설정합니다.
 *
 * 주요 기능:
 * 1. Gateway 서명 검증 (직접 호출 방지)
 * 2. 필수 헤더 검증 (X-User-Id 존재 확인)
 * 3. userId 복호화 (비즈니스 로직용 평문 생성)
 * 4. MDC 설정 (로그 추적용)
 * 5. GatewayContext 설정 (Service/Controller에서 사용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayHeaderInterceptor implements HandlerInterceptor {

    private final CryptoUtil cryptoUtil;
    private final GatewaySignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    @Value("${gateway.signature.enabled:true}")
    private boolean signatureEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        // 1. Gateway 서명 검증
        if (signatureEnabled && !signatureValidator.validateSignature(request)) {
            log.warn("Gateway signature validation failed: {} {}",
                    request.getMethod(), request.getRequestURI());
            sendUnauthorizedResponse(response, "Invalid gateway signature");
            return false;
        }

        // 2. 필수 헤더 검증
        String encryptedUserId = request.getHeader("X-User-Id");
        if (encryptedUserId == null || encryptedUserId.isBlank()) {
            log.warn("Missing X-User-Id header: {} {}",
                    request.getMethod(), request.getRequestURI());
            sendUnauthorizedResponse(response, "인증실패");
            return false;
        }

        // 3. 헤더 추출
        String userNoHeader = request.getHeader("X-User-No");
        String serviceId = request.getHeader("X-Service-Id");
        String role = request.getHeader("X-Role");
        String authHeader = request.getHeader("X-Auth");
        String provider = request.getHeader("X-Provider");
        String nickName = request.getHeader("X-Nick-Name");
        String clientIp = request.getHeader("X-Client-Ip");
        String deviceCd = request.getHeader("X-Device-Cd");
        String deviceStr = request.getHeader("X-Device-Str");

        // 4. userNo 파싱 (평문)
        Long userNo = parseUserNo(userNoHeader);

        // 5. 암호화된 userId 복호화
        String decryptedUserId;
        try {
            decryptedUserId = cryptoUtil.decrypt(encryptedUserId);
        } catch (Exception e) {
            log.error("Failed to decrypt userId: {}", e.getMessage());
            sendUnauthorizedResponse(response, "인증실패");
            return false;
        }

        // 6. 권한 파싱 (JSON 배열)
        List<String> auth = parseAuthHeader(authHeader);

        // 7. MDC 설정 (로깅용)
        if (serviceId != null && !serviceId.isBlank()) {
            MDC.put("serviceId", serviceId);
        }
        if (nickName != null && !nickName.isBlank()) {
            MDC.put("nickName", nickName);
        }

        // 8. GatewayContext 설정
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
                .build();

        GatewayContext.setContext(context);

        if (log.isDebugEnabled()) {
            log.debug("Gateway context initialized - UserNo: {}, Service: {}, Role: {}",
                    userNo, serviceId, role);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.remove("serviceId");
        MDC.remove("nickName");
        GatewayContext.clear();
    }

    private Long parseUserNo(String userNoHeader) {
        if (userNoHeader == null || userNoHeader.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(userNoHeader);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse userNo header: {}", userNoHeader);
            return null;
        }
    }

    private List<String> parseAuthHeader(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return List.of();
        }

        try {
            List<String> auth = objectMapper.readValue(authHeader,
                    new TypeReference<List<String>>(){});
            return auth != null ? auth : List.of();
        } catch (Exception e) {
            log.warn("Failed to parse auth header: {}", e.getMessage());
            return List.of();
        }
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("Unauthorized")
                .message(message)
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}