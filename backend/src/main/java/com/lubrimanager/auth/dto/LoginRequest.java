package com.lubrimanager.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code username} accepte le nom d'utilisateur ou l'adresse e-mail. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
