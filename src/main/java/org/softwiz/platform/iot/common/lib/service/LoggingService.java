package org.softwiz.platform.iot.common.lib.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class LoggingService {

    public void logRequest(String method, String url, String clientIp, String userAgent,
                          String contentType, int contentLength, 
                          Map<String, String> headers, Map<String, String[]> params) {
        log.info("Request: {} {} | IP: {} | UA: {}", method, url, clientIp, userAgent);
        if (log.isDebugEnabled()) {
            log.debug("Headers: {}", headers);
            log.debug("Params: {}", params);
        }
    }

    public void logResponse(String method, String url, int status, String contentType, long duration) {
        log.info("Response: {} {} | Status: {} | {}ms", method, url, status, duration);
    }

    public void logError(String url, String errorCode, String message, Exception ex) {
        log.error("Error at {}: {} - {}", url, errorCode, message, ex);
    }
}