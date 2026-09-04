package com.lubrimanager.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Compte administrateur initial créé au premier démarrage si aucun ADMIN n'existe.
 * À désactiver en production une fois le compte créé et son mot de passe changé.
 */
@ConfigurationProperties("lubrimanager.bootstrap.admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String username,
        String password,
        String fullName) {
}
