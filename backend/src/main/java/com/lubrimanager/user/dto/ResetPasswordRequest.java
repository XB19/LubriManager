package com.lubrimanager.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 10, max = 100, message = "Le mot de passe doit contenir au moins 10 caractères")
        String newPassword) {
}
