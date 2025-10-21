package com.springboot.otplogin.service;

import com.springboot.otplogin.entity.BlacklistedToken;
import com.springboot.otplogin.repository.BlacklistedTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    @Transactional
    public void blacklistToken(String token, String logoutReason, String ipAddress, String userAgent) {
        try {
            Claims claims = extractAllClaims(token);
            String jti = claims.getId();
            String userEmail = claims.getSubject();
            String tokenType = (String) claims.get("type");
            Date expiration = claims.getExpiration();

            if (jti == null) {
                jti = generateJtiFromToken(token);
            }

            Optional<BlacklistedToken> existingToken = blacklistedTokenRepository.findByJti(jti);
            if (existingToken.isPresent()) {
                log.debug("Token {} is already blacklisted", jti);
                return;
            }

            LocalDateTime expiryDate = expiration.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                    .jti(jti)
                    .userEmail(userEmail)
                    .tokenType(tokenType)
                    .expiryDate(expiryDate)
                    .logoutReason(logoutReason)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            blacklistedTokenRepository.save(blacklistedToken);

            log.info("Token blacklisted successfully - User: {}, Type: {}, Reason: {}, IP: {}",
                    userEmail, tokenType, logoutReason, ipAddress);

        } catch (Exception e) {
            log.error("Failed to blacklist token: {}", e.getMessage());
            throw new RuntimeException("Failed to blacklist token", e);
        }
    }

    @Transactional
    public void blacklistAllUserTokens(String userEmail, String logoutReason, String ipAddress, String userAgent) {
        try {
            List<BlacklistedToken> existingBlacklistedTokens =
                    blacklistedTokenRepository.findValidBlacklistedTokensForUser(userEmail, LocalDateTime.now());

            log.info("Blacklisting {} existing tokens for user: {}", existingBlacklistedTokens.size(), userEmail);

            for (BlacklistedToken token : existingBlacklistedTokens) {
                token.setLogoutReason(logoutReason);
                token.setIpAddress(ipAddress);
                token.setUserAgent(userAgent);
            }

            blacklistedTokenRepository.saveAll(existingBlacklistedTokens);

        } catch (Exception e) {
            log.error("Failed to blacklist all tokens for user {}: {}", userEmail, e.getMessage());
        }
    }

    @Transactional
    public void blacklistAllUserTokensByType(String userEmail, String tokenType, String logoutReason, String ipAddress, String userAgent) {
        try {
            int deletedCount = blacklistedTokenRepository.deleteTokensForUserByType(userEmail, tokenType);
            log.info("Invalidated {} {} tokens for user: {}", deletedCount, tokenType, userEmail);
        } catch (Exception e) {
            log.error("Failed to invalidate {} tokens for user {}: {}", tokenType, userEmail, e.getMessage());
        }
    }

    public boolean isTokenBlacklisted(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String jti = claims.getId();

            if (jti == null) {
                jti = generateJtiFromToken(token);
            }

            Optional<BlacklistedToken> blacklistedToken = blacklistedTokenRepository.findByJti(jti);

            if (blacklistedToken.isPresent()) {
                BlacklistedToken tokenEntity = blacklistedToken.get();
                if (tokenEntity.isExpired()) {
                    log.debug("Found expired blacklisted token, cleaning up: {}", jti);
                    blacklistedTokenRepository.delete(tokenEntity);
                    return false;
                }
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking if token is blacklisted: {}", e.getMessage());
            return false;
        }
    }

    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            int deletedCount = blacklistedTokenRepository.deleteExpiredTokens(LocalDateTime.now());
            if (deletedCount > 0) {
                log.info("Cleaned up {} expired blacklisted tokens", deletedCount);
            }
        } catch (Exception e) {
            log.error("Error cleaning up expired tokens: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<BlacklistedToken> getBlacklistedTokensForUser(String userEmail) {
        return blacklistedTokenRepository.findValidBlacklistedTokensForUser(userEmail, LocalDateTime.now());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String generateJtiFromToken(String token) {
        return "jti-" + token.hashCode() + "-" + System.currentTimeMillis();
    }

    public void validateTokenNotBlacklisted(String token) {
        if (isTokenBlacklisted(token)) {
            throw new IllegalArgumentException("Token has been blacklisted");
        }
    }
}