package com.springboot.otplogin.controller;

import com.springboot.otplogin.dto.AuthResponseDto;
import com.springboot.otplogin.entity.User;
import com.springboot.otplogin.security.CustomUserDetails;
import com.springboot.otplogin.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Validated
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = userDetails.getUser();

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName());
        profile.put("isActive", user.getIsActive());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("updatedAt", user.getUpdatedAt());

        log.info("Profile accessed for user: {}", user.getEmail());

        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest updateRequest) {

        User updatedUser = userService.updateUser(userDetails.getUserId(), updateRequest.getName());

        Map<String, Object> response = new HashMap<>();
        response.put("id", updatedUser.getId());
        response.put("email", updatedUser.getEmail());
        response.put("name", updatedUser.getName());
        response.put("isActive", updatedUser.getIsActive());
        response.put("updatedAt", updatedUser.getUpdatedAt());
        response.put("message", "Profile updated successfully");

        log.info("Profile updated for user: {}", updatedUser.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/deactivate")
    public ResponseEntity<Map<String, String>> deactivateAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        userService.deactivateUser(userDetails.getUserId());

        log.info("Account deactivated for user: {}", userDetails.getUsername());

        return ResponseEntity.ok(Map.of(
            "message", "Account deactivated successfully"
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = userDetails.getUser();

        long failedAttemptsLast24h = userService.countFailedAttemptsInLastMinutes(user, 24 * 60);
        long failedAttemptsLast1h = userService.countFailedAttemptsInLastMinutes(user, 60);

        Map<String, Object> stats = new HashMap<>();
        stats.put("email", user.getEmail());
        stats.put("accountStatus", user.getIsActive() ? "Active" : "Inactive");
        stats.put("failedAttemptsLast24Hours", failedAttemptsLast24h);
        stats.put("failedAttemptsLastHour", failedAttemptsLast1h);
        stats.put("memberSince", user.getCreatedAt());

        return ResponseEntity.ok(stats);
    }

    public static class UpdateProfileRequest {
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}