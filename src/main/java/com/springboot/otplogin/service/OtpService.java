package com.springboot.otplogin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    @Value("${app.redis.enabled:true}")
    private boolean redisEnabled;

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String ATTEMPTS_KEY_PREFIX = "otp_attempts:";

    // Fallback in-memory storage for development
    private final Map<String, String> inMemoryOtpStore = new ConcurrentHashMap<>();
    private final Map<String, Integer> inMemoryAttemptsStore = new ConcurrentHashMap<>();
    private final Map<String, Long> inMemoryOtpExpiry = new ConcurrentHashMap<>();

    public String generateOtp(String email) {
        String otp = generateNumericOtp();
        String hashedOtp = passwordEncoder.encode(otp);

        String key = OTP_KEY_PREFIX + email;

        if (isRedisAvailable() && redisEnabled) {
            try {
                redisTemplate.opsForValue().set(key, hashedOtp, otpExpirationMinutes, TimeUnit.MINUTES);
                log.info("OTP generated for email: {} using Redis", email);
            } catch (Exception e) {
                log.warn("Redis OTP storage failed, using fallback: {}", e.getMessage());
                storeOtpInMemory(key, hashedOtp);
                log.info("OTP generated for email: {} using in-memory fallback", email);
            }
        } else {
            storeOtpInMemory(key, hashedOtp);
            log.info("OTP generated for email: {} using in-memory fallback", email);
        }

        return otp;
    }

    public boolean verifyOtp(String email, String providedOtp) {
        String key = OTP_KEY_PREFIX + email;
        String hashedOtp = null;

        // Try Redis first, then fallback
        if (isRedisAvailable() && redisEnabled) {
            try {
                hashedOtp = redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("Redis OTP retrieval failed, using fallback: {}", e.getMessage());
                hashedOtp = getOtpFromMemory(key);
            }
        } else {
            hashedOtp = getOtpFromMemory(key);
        }

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

        if (isRedisAvailable() && redisEnabled) {
            try {
                redisTemplate.delete(key);
                log.info("OTP cleared for email: {} using Redis", email);
            } catch (Exception e) {
                log.warn("Redis OTP clear failed, using fallback: {}", e.getMessage());
                clearOtpFromMemory(key);
                log.info("OTP cleared for email: {} using in-memory fallback", email);
            }
        } else {
            clearOtpFromMemory(key);
            log.info("OTP cleared for email: {} using in-memory fallback", email);
        }
    }

    public boolean isOtpExpired(String email) {
        String key = OTP_KEY_PREFIX + email;

        if (isRedisAvailable() && redisEnabled) {
            try {
                return !redisTemplate.hasKey(key);
            } catch (Exception e) {
                log.warn("Redis OTP expiry check failed, using fallback: {}", e.getMessage());
                return isOtpExpiredInMemory(key);
            }
        } else {
            return isOtpExpiredInMemory(key);
        }
    }

    public long getOtpTtl(String email) {
        String key = OTP_KEY_PREFIX + email;

        if (isRedisAvailable() && redisEnabled) {
            try {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.MINUTES);
                return ttl != null ? ttl : 0;
            } catch (Exception e) {
                log.warn("Redis TTL check failed, using fallback: {}", e.getMessage());
                return getOtpTtlFromMemory(key);
            }
        } else {
            return getOtpTtlFromMemory(key);
        }
    }

    private void incrementAttemptCount(String email) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        if (isRedisAvailable() && redisEnabled) {
            try {
                Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
                if (attempts == 1) {
                    redisTemplate.expire(attemptsKey, Duration.ofMinutes(otpExpirationMinutes));
                }
            } catch (Exception e) {
                log.warn("Redis attempt increment failed, using fallback: {}", e.getMessage());
                incrementAttemptCountInMemory(email);
            }
        } else {
            incrementAttemptCountInMemory(email);
        }
    }

    private int getAttemptCount(String email) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        if (isRedisAvailable() && redisEnabled) {
            try {
                String attempts = redisTemplate.opsForValue().get(attemptsKey);
                return attempts != null ? Integer.parseInt(attempts) : 0;
            } catch (Exception e) {
                log.warn("Redis attempt count retrieval failed, using fallback: {}", e.getMessage());
                return getAttemptCountFromMemory(email);
            }
        } else {
            return getAttemptCountFromMemory(email);
        }
    }

    private void clearAttemptCount(String email) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        if (isRedisAvailable() && redisEnabled) {
            try {
                redisTemplate.delete(attemptsKey);
            } catch (Exception e) {
                log.warn("Redis attempt clear failed, using fallback: {}", e.getMessage());
                clearAttemptCountFromMemory(email);
            }
        } else {
            clearAttemptCountFromMemory(email);
        }
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

    // Helper methods for Redis availability and fallback operations
    private boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("test:connection");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void storeOtpInMemory(String key, String hashedOtp) {
        inMemoryOtpStore.put(key, hashedOtp);
        long expiryTime = System.currentTimeMillis() + (otpExpirationMinutes * 60 * 1000);
        inMemoryOtpExpiry.put(key, expiryTime);
    }

    private String getOtpFromMemory(String key) {
        // Check expiry first
        Long expiryTime = inMemoryOtpExpiry.get(key);
        if (expiryTime != null && System.currentTimeMillis() > expiryTime) {
            inMemoryOtpStore.remove(key);
            inMemoryOtpExpiry.remove(key);
            return null;
        }
        return inMemoryOtpStore.get(key);
    }

    private void clearOtpFromMemory(String key) {
        inMemoryOtpStore.remove(key);
        inMemoryOtpExpiry.remove(key);
    }

    private boolean isOtpExpiredInMemory(String key) {
        Long expiryTime = inMemoryOtpExpiry.get(key);
        if (expiryTime != null && System.currentTimeMillis() > expiryTime) {
            inMemoryOtpStore.remove(key);
            inMemoryOtpExpiry.remove(key);
            return true;
        }
        return !inMemoryOtpStore.containsKey(key);
    }

    private long getOtpTtlFromMemory(String key) {
        Long expiryTime = inMemoryOtpExpiry.get(key);
        if (expiryTime == null) {
            return 0;
        }
        long remainingTime = expiryTime - System.currentTimeMillis();
        return remainingTime > 0 ? remainingTime / (60 * 1000) : 0; // Convert to minutes
    }

    private void incrementAttemptCountInMemory(String email) {
        String key = ATTEMPTS_KEY_PREFIX + email;
        inMemoryAttemptsStore.merge(key, 1, Integer::sum);
    }

    private int getAttemptCountFromMemory(String email) {
        String key = ATTEMPTS_KEY_PREFIX + email;
        return inMemoryAttemptsStore.getOrDefault(key, 0);
    }

    private void clearAttemptCountFromMemory(String email) {
        String key = ATTEMPTS_KEY_PREFIX + email;
        inMemoryAttemptsStore.remove(key);
    }
}