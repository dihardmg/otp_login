package com.springboot.otplogin.exception;

import com.springboot.otplogin.dto.RateLimitExceededResponse;
import com.springboot.otplogin.dto.ValidationErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String[]> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();

            // Group errors by field name
            errors.compute(fieldName, (key, existingMessages) -> {
                if (existingMessages == null) {
                    return new String[]{errorMessage};
                } else {
                    // Append to existing array
                    String[] newMessages = new String[existingMessages.length + 1];
                    System.arraycopy(existingMessages, 0, newMessages, 0, existingMessages.length);
                    newMessages[existingMessages.length] = errorMessage;
                    return newMessages;
                }
            });
        });

        ValidationErrorResponse response = ValidationErrorResponse.of(HttpStatus.BAD_REQUEST.value(), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<RateLimitExceededResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, WebRequest request) {

        RateLimitExceededResponse response = RateLimitExceededResponse.of(
            ex.getMessage(),
            ex.getRetryAfterSeconds(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Limit", "5")
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + ex.getRetryAfterSeconds() * 1000))
                .body(response);
    }
}