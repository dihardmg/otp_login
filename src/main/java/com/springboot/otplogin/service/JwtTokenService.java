package com.springboot.otplogin.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-minutes:15}")
    private long accessTokenExpirationMinutes;

    @Value("${app.jwt.refresh-token-expiration-days:30}")
    private long refreshTokenExpirationDays;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.error("Failed to parse JWT token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "access");
        claims.put("jti", generateJTI());
        return createToken(claims, userDetails.getUsername(),
                Instant.now().plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES));
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("jti", generateJTI());
        return createToken(claims, userDetails.getUsername(),
                Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS));
    }

    public String generateAccessToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "access");
        claims.put("jti", generateJTI());
        return createToken(claims, email,
                Instant.now().plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES));
    }

    public String generateRefreshToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("jti", generateJTI());
        return createToken(claims, email,
                Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS));
    }

    public String generateToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "access");
        claims.put("jti", generateJTI());
        return createToken(claims, email,
                Instant.now().plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES));
    }

    private String createToken(Map<String, Object> claims, String subject, Instant expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Boolean validateToken(String token, String email) {
        try {
            final String username = extractUsername(token);
            return (username.equals(email) && !isTokenExpired(token));
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Boolean isAccessToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "access".equals(claims.get("type"));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "refresh".equals(claims.get("type"));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessTokenExpirationInSeconds() {
        return accessTokenExpirationMinutes * 60;
    }

    public String refreshToken(String refreshToken) {
        try {
            Claims claims = extractAllClaims(refreshToken);
            String email = claims.getSubject();

            if (!isRefreshToken(refreshToken)) {
                throw new IllegalArgumentException("Invalid refresh token type");
            }

            return generateAccessToken(email);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Failed to refresh token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid refresh token", e);
        }
    }

    private String generateJTI() {
        return UUID.randomUUID().toString();
    }
}