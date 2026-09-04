package com.lubrimanager.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Paramètres JWT (DEC-15). Durées acceptées au format court : {@code 15m}, {@code 7d}.
 */
@Validated
@ConfigurationProperties("lubrimanager.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank @Size(min = 32, message = "Le secret JWT doit contenir au moins 32 caractères") String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
