package com.lubrimanager.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Rôles RBAC. Les permissions fines sont dérivées du rôle via {@code @PreAuthorize}
 * côté service (aucune table Permission séparée — voir dossier d'architecture §10).
 */
public enum Role {

    /** Droits complets : utilisateurs, paramètres, corrections, audit. */
    ADMIN,
    /** Supervision : validation des opérations sensibles, rapports, audit. */
    MANAGER,
    /** Caisse : clients, cartes, recharges, ventes, reçus. */
    AGENT,
    /** Consultation seule : toutes les données, aucune modification. */
    AUDITEUR;

    public String authority() {
        return "ROLE_" + name();
    }

    public GrantedAuthority grantedAuthority() {
        return new SimpleGrantedAuthority(authority());
    }
}
