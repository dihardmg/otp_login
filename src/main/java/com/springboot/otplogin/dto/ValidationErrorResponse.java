package com.springboot.otplogin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {

    private String type;
    private String title;
    private int status;
    private String detail;
    private Map<String, String[]> errors;
    private String timestamp;

    public static ValidationErrorResponse of(int status, Map<String, String[]> errors) {
        return ValidationErrorResponse.builder()
                .type("https://example.com/problems/validation-error")
                .title("Validation Failed")
                .status(status)
                .detail("Your request parameters didn't validate correctly.")
                .errors(errors)
                .timestamp(LocalDateTime.now().toString() + "Z")
                .build();
    }
}