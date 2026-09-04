package com.lubrimanager.auth.dto;

import com.lubrimanager.user.Role;
import com.lubrimanager.user.User;

public record MeResponse(
        Long id,
        String username,
        String fullName,
        String email,
        Role role,
        Long branchId) {

    public static MeResponse from(User u) {
        return new MeResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                u.getRole(), u.getBranch().getId());
    }
}
