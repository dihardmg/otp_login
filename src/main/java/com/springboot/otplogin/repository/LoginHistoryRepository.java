package com.springboot.otplogin.repository;

import com.springboot.otplogin.entity.LoginHistory;
import com.springboot.otplogin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT COUNT(lh) FROM LoginHistory lh WHERE lh.user = :user AND lh.successful = false AND lh.createdAt > :since")
    long countFailedAttemptsSince(@Param("user") User user, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(lh) FROM LoginHistory lh WHERE lh.ipAddress = :ipAddress AND lh.successful = false AND lh.createdAt > :since")
    long countFailedAttemptsByIpSince(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
}