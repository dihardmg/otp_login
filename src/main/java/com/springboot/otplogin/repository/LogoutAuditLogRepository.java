package com.springboot.otplogin.repository;

import com.springboot.otplogin.entity.LogoutAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogoutAuditLogRepository extends JpaRepository<LogoutAuditLog, Long> {

    List<LogoutAuditLog> findByUserEmailOrderByLogoutTimeDesc(String userEmail);

    @Query("SELECT lal FROM LogoutAuditLog lal WHERE lal.userEmail = :email AND lal.logoutTime >= :since")
    List<LogoutAuditLog> findByUserEmailAndLogoutTimeAfter(@Param("email") String email, @Param("since") LocalDateTime since);

    @Query("SELECT lal FROM LogoutAuditLog lal WHERE lal.logoutTime >= :since AND lal.logoutTime <= :until")
    List<LogoutAuditLog> findByLogoutTimeBetween(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Query("SELECT COUNT(lal) FROM LogoutAuditLog lal WHERE lal.userEmail = :email AND lal.logoutTime >= :since")
    Long countByUserEmailAndLogoutTimeAfter(@Param("email") String email, @Param("since") LocalDateTime since);

    @Query("SELECT lal FROM LogoutAuditLog lal WHERE lal.ipAddress = :ipAddress ORDER BY lal.logoutTime DESC")
    List<LogoutAuditLog> findByIpAddressOrderByLogoutTimeDesc(@Param("ipAddress") String ipAddress);

    @Query("SELECT lal FROM LogoutAuditLog lal WHERE lal.success = false ORDER BY lal.logoutTime DESC")
    List<LogoutAuditLog> findFailedLogoutsOrderByLogoutTimeDesc();

    @Query("SELECT lal.logoutType, COUNT(lal) FROM LogoutAuditLog lal WHERE lal.logoutTime >= :since GROUP BY lal.logoutType")
    List<Object[]> countByLogoutTypeSince(@Param("since") LocalDateTime since);
}