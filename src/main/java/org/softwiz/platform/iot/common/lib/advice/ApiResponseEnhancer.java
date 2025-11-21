package org.softwiz.platform.iot.common.lib.advice;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import org.softwiz.platform.iot.common.lib.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * ApiResponse에 Request ID를 자동으로 주입
 * MdcFilter에서 설정한 MDC의 requestId를 사용
 */
@Slf4j
@RestControllerAdvice
public class ApiResponseEnhancer implements ResponseBodyAdvice<Object> {

    private static final String REQUEST_ID = "requestId";

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        if (body instanceof ApiResponse) {
            ApiResponse<?> apiResponse = (ApiResponse<?>) body;
            String requestId = MDC.get(REQUEST_ID);

            if (apiResponse.getRequestId() == null && requestId != null) {
                apiResponse.setRequestId(requestId);
                log.debug("Request ID injected into ApiResponse: {}", requestId);
            }
        }

        return body;
    }
}