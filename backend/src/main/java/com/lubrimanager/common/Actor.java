package com.lubrimanager.common;

/**
 * Identité minimale de l'utilisateur qui exécute une opération, transmise du
 * contrôleur vers la couche service sans faire fuiter les types de sécurité.
 */
public record Actor(Long id, String username, Long branchId) {
}
