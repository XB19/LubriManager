package com.lubrimanager.user.dto;

import com.lubrimanager.user.Role;
import com.lubrimanager.user.User;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Role role,
        boolean active,
        Long branchId,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.getRole(),
                u.isActive(),
                u.getBranch().getId(),
                u.getLastLoginAt(),
                u.getCreatedAt());
    }
}
