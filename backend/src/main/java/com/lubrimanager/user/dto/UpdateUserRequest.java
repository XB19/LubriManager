package com.lubrimanager.user.dto;

import com.lubrimanager.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Email @Size(max = 160) String email,
        @NotNull Role role) {
}
