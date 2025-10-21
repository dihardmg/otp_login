package com.springboot.otplogin.service;

import com.springboot.otplogin.entity.LogoutAuditLog;
import com.springboot.otplogin.repository.LogoutAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogoutAuditService {

    private final LogoutAuditLogRepository logoutAuditLogRepository;

    @Transactional
    public void logLogout(String userEmail, String logoutType, String logoutReason,
                         String ipAddress, String userAgent, Integer sessionsTerminated,
                         Boolean success, String errorMessage) {

        try {
            LogoutAuditLog auditLog = LogoutAuditLog.builder()
                    .userEmail(userEmail)
                    .logoutType(logoutType)
                    .logoutReason(logoutReason)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .sessionsTerminated(sessionsTerminated != null ? sessionsTerminated : 1)
                    .success(success != null ? success : true)
                    .errorMessage(errorMessage)
                    .requestId(UUID.randomUUID().toString())
                    .build();

            logoutAuditLogRepository.save(auditLog);

            if (success) {
                log.info("Logout audit log created - User: {}, Type: {}, IP: {}, Sessions: {}",
                        userEmail, logoutType, ipAddress, sessionsTerminated);
            } else {
                log.warn("Logout failed audit log created - User: {}, Type: {}, Error: {}",
                        userEmail, logoutType, errorMessage);
            }

        } catch (Exception e) {
            log.error("Failed to create logout audit log: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<LogoutAuditLog> getUserLogoutHistory(String userEmail) {
        return logoutAuditLogRepository.findByUserEmailOrderByLogoutTimeDesc(userEmail);
    }

    @Transactional(readOnly = true)
    public List<LogoutAuditLog> getUserLogoutHistorySince(String userEmail, LocalDateTime since) {
        return logoutAuditLogRepository.findByUserEmailAndLogoutTimeAfter(userEmail, since);
    }

    @Transactional(readOnly = true)
    public List<LogoutAuditLog> getLogoutHistoryBetween(LocalDateTime start, LocalDateTime end) {
        return logoutAuditLogRepository.findByLogoutTimeBetween(start, end);
    }

    @Transactional(readOnly = true)
    public Long countUserLogoutsSince(String userEmail, LocalDateTime since) {
        return logoutAuditLogRepository.countByUserEmailAndLogoutTimeAfter(userEmail, since);
    }

    @Transactional(readOnly = true)
    public List<LogoutAuditLog> getLogoutsByIpAddress(String ipAddress) {
        return logoutAuditLogRepository.findByIpAddressOrderByLogoutTimeDesc(ipAddress);
    }

    @Transactional(readOnly = true)
    public List<LogoutAuditLog> getFailedLogouts() {
        return logoutAuditLogRepository.findFailedLogoutsOrderByLogoutTimeDesc();
    }

    @Transactional(readOnly = true)
    public Page<LogoutAuditLog> getAllLogoutLogs(Pageable pageable) {
        return logoutAuditLogRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Object[]> getLogoutStatisticsSince(LocalDateTime since) {
        return logoutAuditLogRepository.countByLogoutTypeSince(since);
    }

    @Transactional
    public void cleanupOldLogs(LocalDateTime cutoffDate) {
        try {
            List<LogoutAuditLog> oldLogs = logoutAuditLogRepository.findByLogoutTimeBetween(
                    LocalDateTime.of(1970, 1, 1, 0, 0), cutoffDate);

            int deletedCount = oldLogs.size();
            logoutAuditLogRepository.deleteAll(oldLogs);

            log.info("Cleaned up {} old logout audit logs", deletedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup old logout audit logs: {}", e.getMessage(), e);
        }
    }
}