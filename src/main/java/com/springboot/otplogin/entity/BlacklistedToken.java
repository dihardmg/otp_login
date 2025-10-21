package com.springboot.otplogin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklisted_tokens", indexes = {
    @Index(name = "idx_token_jti", columnList = "jti"),
    @Index(name = "idx_user_email", columnList = "userEmail"),
    @Index(name = "idx_expiry_date", columnList = "expiryDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jti", nullable = false, unique = true)
    private String jti;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "token_type", nullable = false)
    private String tokenType;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "logout_reason")
    private String logoutReason;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @CreatedDate
    @Column(name = "blacklisted_at", nullable = false, updatable = false)
    private LocalDateTime blacklistedAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
}