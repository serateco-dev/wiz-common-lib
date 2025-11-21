package org.softwiz.platform.iot.common.lib.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private T data;

    private String message;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private String requestId;

    /**
     * 페이징 정보 (공통 라이브러리의 PageInfo)
     */
    private PageInfo pageInfo;


    // 기본 성공 응답

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .data(data)
                .message(message)
                .build();
    }

    // 페이징 성공 응답

    public static <T> ApiResponse<T> success(T data, PageInfo pageInfo) {
        return ApiResponse.<T>builder()
                .data(data)
                .pageInfo(pageInfo)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message, PageInfo pageInfo) {
        return ApiResponse.<T>builder()
                .data(data)
                .message(message)
                .pageInfo(pageInfo)
                .build();
    }

    public static <T> ApiResponse<T> successWithMessage(String message) {
        return ApiResponse.<T>builder()
                .message(message)
                .build();
    }
}