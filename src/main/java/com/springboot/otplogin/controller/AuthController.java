package com.springboot.otplogin.controller;

import com.springboot.otplogin.dto.AuthResponseDto;
import com.springboot.otplogin.dto.OtpRequestDto;
import com.springboot.otplogin.dto.OtpVerificationDto;
import com.springboot.otplogin.dto.SignupRequestDto;
import com.springboot.otplogin.exception.RateLimitExceededException;
import com.springboot.otplogin.entity.User;
import com.springboot.otplogin.service.EmailService;
import com.springboot.otplogin.service.JwtTokenService;
import com.springboot.otplogin.service.OtpService;
import com.springboot.otplogin.service.UserService;
import com.springboot.otplogin.service.TokenBlacklistService;
import com.springboot.otplogin.service.LogoutAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
    private final TokenBlacklistService tokenBlacklistService;
    private final LogoutAuditService logoutAuditService;

    
    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(
            @Valid @RequestBody OtpRequestDto otpRequestDto,
            HttpServletRequest request) {

        String email = otpRequestDto.getEmail();
        String clientIp = getClientIpAddress(request);

        // Check OTP service rate limiting (additional layer)
        if (otpService.isRateLimited(email)) {
            throw new RateLimitExceededException("Too many OTP attempts. Please try again later.", 60);
        }

        try {
            // Check if user exists and is active in database
            if (!userService.userExistsAndIsActive(email)) {
                // Check if user exists but is inactive
                Optional<User> userOpt = userService.findUserByEmail(email);
                if (userOpt.isPresent() && !userOpt.get().getIsActive()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "User account is inactive. Please contact support."));
                }
                // User doesn't exist
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "INVALID MAIL"));
            }

            User user = userService.getUserByEmail(email);

            if (userService.isRateLimited(user, 5, 15)) {
                throw new RateLimitExceededException("Account temporarily locked due to too many failed attempts. Please try again in 15 minutes.", 900);
            }

            if (userService.isIpRateLimited(clientIp, 10, 15)) {
                throw new RateLimitExceededException("IP temporarily blocked due to too many failed attempts. Please try again in 15 minutes.", 900);
            }

            String otp = otpService.generateOtp(email);

            try {
                emailService.sendOtpEmail(email, otp);
            } catch (Exception e) {
                log.warn("Email sending failed: {}", e.getMessage());
                // Continue even if email fails for development
            }

            log.info("OTP requested for email: {} from IP: {}. OTP: {} (DEV MODE)", email, clientIp, otp);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "OTP has been sent to your email");
            response.put("email", email);
            response.put("expiresIn", otpService.getOtpTtl(email));

            return ResponseEntity.ok(response);

        } catch (RateLimitExceededException e) {
            // Re-throw rate limit exceptions to be handled by global exception handler
            throw e;
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

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(
            @Valid @RequestBody SignupRequestDto signupRequestDto,
            HttpServletRequest request) {

        String email = signupRequestDto.getEmail();
        String name = signupRequestDto.getName();
        String clientIp = getClientIpAddress(request);
        try {
            // Check if user exists and create in one operation to minimize DB calls
            User newUser = userService.createUserIfNotExists(email, name);
            if (newUser == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "User with this email already exists"));
            }

            // Send email asynchronously - don't block response
            try {
                emailService.sendWelcomeEmailAsync(email, name);
            } catch (Exception e) {
                // Log only if needed for debugging
                log.debug("Welcome email sending failed for {}: {}", email, e.getMessage());
            }

            // Minimal logging for performance
            if (log.isDebugEnabled()) {
                log.debug("New user created - Email: {}, IP: {}", email, clientIp);
            }

            // Pre-built response object for faster serialization
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "User registered successfully",
                            "email", email,
                            "name", name,
                            "userId", String.valueOf(newUser.getId())
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error during user signup for {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed. Please try again."));
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
}