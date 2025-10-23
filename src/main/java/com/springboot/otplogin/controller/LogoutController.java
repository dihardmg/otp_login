package com.springboot.otplogin.controller;

import com.springboot.otplogin.service.TokenBlacklistService;
import com.springboot.otplogin.service.OtpService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class LogoutController {

    private final TokenBlacklistService tokenBlacklistService;
    private final OtpService otpService;

    private final Map<String, Bucket> ipBucketMap = new ConcurrentHashMap<>();

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                tokenBlacklistService.blacklistToken(token, "User logout", clientIp, userAgent);

                String email = extractEmailFromToken(token);

                // Blacklist all refresh tokens for the user to prevent token reuse
                tokenBlacklistService.blacklistAllUserTokensByType(email, "refresh",
                        "User logout - invalidate refresh tokens", clientIp, userAgent);

                otpService.clearOtp(email);

                log.info("User logged out successfully - Email: {}, IP: {}, User-Agent: {}",
                        email, clientIp, userAgent);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Logged out successfully",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            ));

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Logout failed. Please try again."));
        }
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAllDevices(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String email = extractEmailFromToken(token);

                tokenBlacklistService.blacklistToken(token, "Logout all devices", clientIp, userAgent);
                tokenBlacklistService.blacklistAllUserTokens(email, "Logout all devices", clientIp, userAgent);

                otpService.clearOtp(email);

                log.info("User logged out from all devices - Email: {}, IP: {}, User-Agent: {}",
                        email, clientIp, userAgent);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Logged out from all devices successfully",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            ));

        } catch (Exception e) {
            log.error("Logout all devices failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Logout from all devices failed. Please try again."));
        }
    }

    @PostMapping("/invalidate-refresh-tokens")
    public ResponseEntity<Map<String, String>> invalidateRefreshTokens(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        Bucket ipBucket = getBucketForIp(clientIp);
        if (!ipBucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many requests. Please try again later."));
        }

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String email = extractEmailFromToken(token);

                tokenBlacklistService.blacklistAllUserTokensByType(email, "refresh",
                        "Refresh token invalidation", clientIp, userAgent);

                log.info("All refresh tokens invalidated for user - Email: {}, IP: {}",
                        email, clientIp);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "All refresh tokens invalidated successfully",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            ));

        } catch (Exception e) {
            log.error("Refresh token invalidation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Refresh token invalidation failed. Please try again."));
        }
    }

    @PostMapping("/force-logout")
    public ResponseEntity<Map<String, String>> forceLogout(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String clientIp = request.get("ipAddress");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email is required"));
        }

        try {
            tokenBlacklistService.blacklistAllUserTokens(email,
                    "Force logout by admin", clientIp, "Admin System");

            otpService.clearOtp(email);

            log.warn("Force logout executed for user - Email: {}, Requested from IP: {}",
                    email, clientIp);

            return ResponseEntity.ok(Map.of(
                    "message", "User has been forcefully logged out from all devices",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            ));

        } catch (Exception e) {
            log.error("Force logout failed for {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Force logout failed. Please try again."));
        }
    }

    private String extractEmailFromToken(String token) {
        try {
            io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser()
                    .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                            System.getProperty("app.jwt.secret", "mySecretKey").getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Failed to extract email from token: {}", e.getMessage());
            return "unknown";
        }
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
            Bandwidth limit = Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1)));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }
}