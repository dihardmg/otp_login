package com.springboot.otplogin.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.otplogin.dto.RateLimitExceededResponse;
import com.springboot.otplogin.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> ipBucketMap = new ConcurrentHashMap<>();
    private final Map<String, Bucket> emailBucketMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Only apply rate limiting to OTP request endpoint
        if (httpRequest.getRequestURI().equals("/api/v1/auth/request-otp")
                && "POST".equals(httpRequest.getMethod())) {

            // Wrap request to allow multiple reads
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);

            // Extract email from request body for email-based rate limiting
            String email = extractEmailFromRequest(wrappedRequest);
            String clientIp = getClientIpAddress(httpRequest);

            // Check IP-based rate limiting
            Bucket ipBucket = getBucketForIp(clientIp);
            if (!ipBucket.tryConsume(1)) {
                sendRateLimitResponse(httpResponse, "IP rate limit exceeded. Too many requests from this IP address.", 60);
                return;
            }

            // Check email-based rate limiting if email was extracted
            if (email != null && !email.isEmpty()) {
                Bucket emailBucket = getBucketForEmail(email);
                if (!emailBucket.tryConsume(1)) {
                    sendRateLimitResponse(httpResponse, "Email rate limit exceeded. Too many OTP requests for this email address.", 60);
                    return;
                }
            }

            // Continue with wrapped request
            chain.doFilter(wrappedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private String extractEmailFromRequest(ContentCachingRequestWrapper request) {
        try {
            byte[] content = request.getContentAsByteArray();
            if (content.length > 0) {
                String body = new String(content);
                // Simple JSON parsing to extract email
                if (body.contains("\"email\"")) {
                    int emailIndex = body.indexOf("\"email\"");
                    int colonIndex = body.indexOf(":", emailIndex);
                    int startIndex = body.indexOf("\"", colonIndex) + 1;
                    int endIndex = body.indexOf("\"", startIndex);
                    if (startIndex > 0 && endIndex > startIndex) {
                        return body.substring(startIndex, endIndex);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract email from request: {}", e.getMessage());
        }
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private Bucket getBucketForIp(String ip) {
        return ipBucketMap.computeIfAbsent(ip, ignored -> {
            Bandwidth limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    private Bucket getBucketForEmail(String email) {
        return emailBucketMap.computeIfAbsent(email, ignored -> {
            Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    private void sendRateLimitResponse(HttpServletResponse response, String message, long retryAfterSeconds)
            throws IOException {
        RateLimitExceededResponse rateLimitResponse = RateLimitExceededResponse.of(
            message,
            retryAfterSeconds,
            "/api/v1/auth/request-otp"
        );

        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", "5");
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + retryAfterSeconds * 1000));

        response.getWriter().write(objectMapper.writeValueAsString(rateLimitResponse));
    }
}