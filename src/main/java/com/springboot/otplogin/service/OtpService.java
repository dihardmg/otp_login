package com.springboot.otplogin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.max-attempts:3}")
    private int maxOtpAttempts;

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String ATTEMPTS_KEY_PREFIX = "otp_attempts:";

    public String generateOtp(String email) {
        String otp = generateNumericOtp();
        String hashedOtp = passwordEncoder.encode(otp);

        String key = OTP_KEY_PREFIX + email;

        redisTemplate.opsForValue().set(key, hashedOtp, otpExpirationMinutes, TimeUnit.MINUTES);

        log.info("OTP generated for email: {}", email);

        return otp;
    }

    public boolean verifyOtp(String email, String providedOtp) {
        String key = OTP_KEY_PREFIX + email;
        String hashedOtp = redisTemplate.opsForValue().get(key);

        if (hashedOtp == null) {
            log.warn("OTP not found or expired for email: {}", email);
            return false;
        }

        incrementAttemptCount(email);

        if (getAttemptCount(email) > maxOtpAttempts) {
            log.warn("Too many OTP attempts for email: {}", email);
            clearOtp(email);
            return false;
        }

        boolean isValid = passwordEncoder.matches(providedOtp, hashedOtp);

        if (isValid) {
            clearOtp(email);
            clearAttemptCount(email);
            log.info("OTP verified successfully for email: {}", email);
        } else {
            log.warn("Invalid OTP attempt for email: {}", email);
        }

        return isValid;
    }

    public void clearOtp(String email) {
        String key = OTP_KEY_PREFIX + email;
        redisTemplate.delete(key);
        log.info("OTP cleared for email: {}", email);
    }

    public boolean isOtpExpired(String email) {
        String key = OTP_KEY_PREFIX + email;
        return !redisTemplate.hasKey(key);
    }

    public long getOtpTtl(String email) {
        String key = OTP_KEY_PREFIX + email;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.MINUTES);
        return ttl != null ? ttl : 0;
    }

    private void incrementAttemptCount(String email) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts == 1) {
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(otpExpirationMinutes));
        }
    }

    private int getAttemptCount(String email) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;
        String attempts = redisTemplate.opsForValue().get(attemptsKey);
        return attempts != null ? Integer.parseInt(attempts) : 0;
    }

    private void clearAttemptCount(String email) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;
        redisTemplate.delete(attemptsKey);
    }

    private String generateNumericOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    public boolean isRateLimited(String email) {
        return getAttemptCount(email) >= maxOtpAttempts;
    }
}