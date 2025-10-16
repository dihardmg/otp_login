package com.springboot.otplogin.controller;

import com.springboot.otplogin.dto.AuthResponseDto;
import com.springboot.otplogin.dto.OtpRequestDto;
import com.springboot.otplogin.dto.OtpVerificationDto;
import com.springboot.otplogin.entity.User;
import com.springboot.otplogin.service.EmailService;
import com.springboot.otplogin.service.JwtTokenService;
import com.springboot.otplogin.service.OtpService;
import com.springboot.otplogin.service.UserService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AuthController {

    private final UserService userService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final JwtTokenService jwtTokenService;

    private final Map<String, Bucket> ipBucketMap = new ConcurrentHashMap<>();
    private final Map<String, Bucket> emailBucketMap = new ConcurrentHashMap<>();

    @PostMapping("/request-otp")
    public ResponseEntity<Map<String, String>> requestOtp(
            @Valid @RequestBody OtpRequestDto otpRequestDto,
            HttpServletRequest request) {

        String email = otpRequestDto.getEmail();
        String clientIp = getClientIpAddress(request);

        Bucket ipBucket = getBucketForIp(clientIp);
        if (!ipBucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many requests. Please try again later."));
        }

        Bucket emailBucket = getBucketForEmail(email);
        if (!emailBucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many OTP attempts. Please try again later."));
        }

        if (otpService.isRateLimited(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many OTP attempts. Please try again later."));
        }

        try {
            // Check if user exists in database
            if (!userService.userExists(email)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "INVALID MAIL"));
            }

            User user = userService.getUserByEmail(email);

            if (userService.isRateLimited(user, 5, 15)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "Account temporarily locked due to too many failed attempts. Please try again in 15 minutes."));
            }

            if (userService.isIpRateLimited(clientIp, 10, 15)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "IP temporarily blocked due to too many failed attempts. Please try again in 15 minutes."));
            }

            String otp = otpService.generateOtp(email);

            try {
                emailService.sendOtpEmail(email, otp);
            } catch (Exception e) {
                log.warn("Email sending failed: {}", e.getMessage());
                // Continue even if email fails for development
            }

            log.info("OTP requested for email: {} from IP: {}. OTP: {} (DEV MODE)", email, clientIp, otp);

            Map<String, String> response = new HashMap<>();
            response.put("message", "OTP has been sent to your email");
            response.put("email", email);
            response.put("expiresIn", String.valueOf(otpService.getOtpTtl(email)));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error sending OTP to {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to send OTP. Please try again."));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @Valid @RequestBody OtpVerificationDto otpVerificationDto,
            HttpServletRequest request) {

        String email = otpVerificationDto.getEmail();
        String otp = otpVerificationDto.getOtp();
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        try {
            User user = userService.getUserByEmail(email);

            if (!user.getIsActive()) {
                userService.recordLoginHistory(user, clientIp, userAgent, false, "Account is inactive");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Account is deactivated"));
            }

            if (userService.isRateLimited(user, 5, 15)) {
                userService.recordLoginHistory(user, clientIp, userAgent, false, "Account rate limited");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "Account temporarily locked. Please try again in 15 minutes."));
            }

            if (otpService.verifyOtp(email, otp)) {
                String accessToken = jwtTokenService.generateAccessToken(user.getEmail());
                String refreshToken = jwtTokenService.generateRefreshToken(user.getEmail());

                userService.recordLoginHistory(user, clientIp, userAgent, true, null);

                log.info("Successful OTP verification for email: {} from IP: {}", email, clientIp);

                AuthResponseDto response = AuthResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenType("Bearer")
                        .expiresIn(jwtTokenService.getAccessTokenExpirationInSeconds())
                        .email(email)
                        .build();

                return ResponseEntity.ok(response);
            } else {
                userService.recordLoginHistory(user, clientIp, userAgent, false, "Invalid OTP");

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid OTP. Please try again."));
            }

        } catch (Exception e) {
            log.error("Error verifying OTP for {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "OTP verification failed. Please try again."));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Refresh token is required"));
        }

        try {
            if (!jwtTokenService.isRefreshToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid refresh token"));
            }

            String email = jwtTokenService.extractUsername(refreshToken);
            User user = userService.getUserByEmail(email);

            if (!user.getIsActive()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Account is deactivated"));
            }

            String newAccessToken = jwtTokenService.refreshToken(refreshToken);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", jwtTokenService.getAccessTokenExpirationInSeconds());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error refreshing token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired refresh token"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtTokenService.isAccessToken(token)) {
                String email = jwtTokenService.extractUsername(token);
                otpService.clearOtp(email);
                log.info("User logged out: {}", email);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
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
        return ipBucketMap.computeIfAbsent(ip, k -> {
            Bandwidth limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    private Bucket getBucketForEmail(String email) {
        return emailBucketMap.computeIfAbsent(email, k -> {
            // Limit to 5 requests per minute per email
            Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }
}