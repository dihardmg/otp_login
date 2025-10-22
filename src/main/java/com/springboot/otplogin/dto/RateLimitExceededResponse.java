package com.springboot.otplogin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitExceededResponse {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private long retryAfter;
    private String timestamp;

    public static RateLimitExceededResponse of(String detail, long retryAfterSeconds, String path) {
        return RateLimitExceededResponse.builder()
                .type("https://example.com/problems/rate-limit-exceeded")
                .title("Too Many Requests")
                .status(429)
                .detail(detail)
                .instance(path)
                .retryAfter(retryAfterSeconds)
                .timestamp(LocalDateTime.now().toString() + "Z")
                .build();
    }
}