package org.softwiz.platform.iot.common.lib.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 클라이언트 IP 추출 유틸리티
 *
 * 역할:
 * - 게이트웨이가 전달한 X-Client-Ip 헤더 우선 사용
 * - 없을 경우 다양한 프록시 헤더에서 추출 (fallback)
 */
@Slf4j
@Component
public class ClientIpExtractor {

    /**
     * 클라이언트 IP 추출
     *
     * @param request HTTP 요청
     * @return 클라이언트 IP 주소
     */
    public String extractClientIp(HttpServletRequest request) {
        // 1. 게이트웨이가 추출한 Client IP (가장 정확함)
        String ip = request.getHeader("X-Client-IP");

        if (isValidIp(ip)) {
            log.debug("Using Client IP from gateway: {}", ip);
            return ip;
        }

        // 2. 게이트웨이를 거치지 않은 경우 자체 추출 (fallback)
        log.debug("X-Client-Ip header missing, extracting from request headers");
        return extractFromHeaders(request);
    }

    /**
     * 다양한 프록시 헤더에서 IP 추출
     *
     * @param request HTTP 요청
     * @return 추출된 IP 주소
     */
    private String extractFromHeaders(HttpServletRequest request) {
        // 우선순위대로 헤더 검사
        String[] headerNames = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR",
                "X-Real-IP"
        };

        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (isValidIp(ip)) {
                // X-Forwarded-For는 여러 IP가 있을 수 있음 (첫 번째가 실제 클라이언트)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                log.debug("Client IP extracted from header {}: {}", headerName, ip);
                return ip;
            }
        }

        // 모든 헤더에서 찾지 못한 경우 Remote Address 사용
        String remoteAddr = request.getRemoteAddr();
        log.debug("Client IP extracted from RemoteAddr: {}", remoteAddr);
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /**
     * 유효한 IP인지 검증
     *
     * @param ip 검증할 IP
     * @return 유효하면 true
     */
    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}