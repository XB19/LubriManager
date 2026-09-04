package com.lubrimanager.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Verrouillage de compte après échecs répétés d'authentification (DEC-14).
 */
@ConfigurationProperties("lubrimanager.security.lockout")
public record LockoutProperties(int maxFailedAttempts, Duration lockDuration) {
}
