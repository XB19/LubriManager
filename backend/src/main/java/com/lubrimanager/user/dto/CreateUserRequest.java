package com.lubrimanager.user.dto;

import com.lubrimanager.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 60)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Caractères autorisés : lettres, chiffres, . _ -")
        String username,

        @Email @Size(max = 160)
        String email,

        @NotBlank @Size(min = 10, max = 100, message = "Le mot de passe doit contenir au moins 10 caractères")
        String password,

        @NotBlank @Size(max = 120)
        String fullName,

        @NotNull
        Role role,

        Long branchId) {
}
