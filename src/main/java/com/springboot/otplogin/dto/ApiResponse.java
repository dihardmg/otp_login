package com.springboot.otplogin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String status;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .status("OK")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(int code, String status, T data) {
        return ApiResponse.<T>builder()
                .code(code)
                .status(status)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String status, T data) {
        return ApiResponse.<T>builder()
                .code(code)
                .status(status)
                .data(data)
                .build();
    }
}