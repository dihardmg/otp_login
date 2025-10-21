package com.springboot.otplogin.service;

import com.springboot.otplogin.entity.LoginHistory;
import com.springboot.otplogin.entity.User;
import com.springboot.otplogin.repository.LoginHistoryRepository;
import com.springboot.otplogin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = findByEmailAndIsActive(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return new com.springboot.otplogin.security.CustomUserDetails(user);
    }

    public Optional<User> findByEmailAndIsActive(String email) {
        return userRepository.findByEmailAndIsActive(email);
    }

    public boolean userExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean userExistsAndIsActive(String email) {
        return userRepository.findByEmailAndIsActive(email).isPresent();
    }

    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User getOrCreateUser(String email, String name) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        User newUser = User.builder()
                .email(email)
                .name(name != null ? name : email.substring(0, email.indexOf('@')))
                .isActive(true)
                .build();

        return userRepository.save(newUser);
    }

    public User createUser(String email, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        User newUser = User.builder()
                .email(email)
                .name(name != null ? name : email.substring(0, email.indexOf('@')))
                .isActive(true)
                .build();

        return userRepository.save(newUser);
    }

    // Optimized method that combines exists check and creation in one transaction
    @Transactional
    public User createUserIfNotExists(String email, String name) {
        // Try to find user first with a direct query
        User existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser != null) {
            return null; // User exists
        }

        // Create new user
        User newUser = User.builder()
                .email(email)
                .name(name != null ? name : email.substring(0, email.indexOf('@')))
                .isActive(true)
                .build();

        return userRepository.save(newUser);
    }

    public User updateUser(Long userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        user.setName(name);
        return userRepository.save(user);
    }

    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        user.setIsActive(false);
        userRepository.save(user);
    }

    public void recordLoginHistory(User user, String ipAddress, String userAgent, boolean successful, String failureReason) {
        LoginHistory loginHistory = LoginHistory.builder()
                .user(user)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .successful(successful)
                .failureReason(failureReason)
                .build();

        loginHistoryRepository.save(loginHistory);

        if (successful) {
            log.info("Successful login recorded for user: {} from IP: {}", user.getEmail(), ipAddress);
        } else {
            log.warn("Failed login attempt for user: {} from IP: {}, reason: {}", user.getEmail(), ipAddress, failureReason);
        }
    }

    public long countFailedAttemptsInLastMinutes(User user, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return loginHistoryRepository.countFailedAttemptsSince(user, since);
    }

    public long countFailedAttemptsByIpInLastMinutes(String ipAddress, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return loginHistoryRepository.countFailedAttemptsByIpSince(ipAddress, since);
    }

    public boolean isRateLimited(User user, int maxAttempts, int timeWindowMinutes) {
        long failedAttempts = countFailedAttemptsInLastMinutes(user, timeWindowMinutes);
        return failedAttempts >= maxAttempts;
    }

    public boolean isIpRateLimited(String ipAddress, int maxAttempts, int timeWindowMinutes) {
        long failedAttempts = countFailedAttemptsByIpInLastMinutes(ipAddress, timeWindowMinutes);
        return failedAttempts >= maxAttempts;
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }
}