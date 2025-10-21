package com.springboot.otplogin.repository;

import com.springboot.otplogin.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    Optional<BlacklistedToken> findByJti(String jti);

    boolean existsByJti(String jti);

    @Modifying
    @Query("DELETE FROM BlacklistedToken bt WHERE bt.expiryDate < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    @Query("SELECT bt FROM BlacklistedToken bt WHERE bt.userEmail = :email AND bt.expiryDate > :now")
    List<BlacklistedToken> findValidBlacklistedTokensForUser(@Param("email") String email, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM BlacklistedToken bt WHERE bt.userEmail = :email")
    int deleteAllTokensForUser(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM BlacklistedToken bt WHERE bt.userEmail = :email AND bt.tokenType = :tokenType")
    int deleteTokensForUserByType(@Param("email") String email, @Param("tokenType") String tokenType);
}