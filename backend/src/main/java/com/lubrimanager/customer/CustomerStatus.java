package com.lubrimanager.customer;

/**
 * Cycle de vie d'un client. Seul un client {@link #ACTIF} pourra réaliser des
 * opérations financières (contrôle appliqué à partir de la Phase 4).
 */
public enum CustomerStatus {
    ACTIF,
    SUSPENDU,
    BLOQUE,
    ARCHIVE
}
