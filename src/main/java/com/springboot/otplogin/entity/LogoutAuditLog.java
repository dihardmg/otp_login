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
@Table(name = "logout_audit_logs", indexes = {
    @Index(name = "idx_user_email", columnList = "userEmail"),
    @Index(name = "idx_logout_time", columnList = "logoutTime"),
    @Index(name = "idx_ip_address", columnList = "ipAddress"),
    @Index(name = "idx_logout_type", columnList = "logoutType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LogoutAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "logout_type", nullable = false)
    private String logoutType;

    @Column(name = "logout_reason")
    private String logoutReason;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "session_terminated_count", nullable = false)
    @Builder.Default
    private Integer sessionsTerminated = 0;

    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = true;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "request_id")
    private String requestId;

    @CreatedDate
    @Column(name = "logout_time", nullable = false, updatable = false)
    private LocalDateTime logoutTime;
}