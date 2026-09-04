package com.lubrimanager.common.error;

import org.springframework.http.HttpStatus;

/**
 * Catalogue centralisé des erreurs métier et techniques (DEC-21 : messages en
 * français). Chaque code porte un statut HTTP et un message par défaut lisible
 * par un employé ; le détail technique reste dans les logs serveur.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Les données saisies sont invalides."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "Identifiants invalides."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Session expirée, veuillez vous reconnecter."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Jeton d'authentification invalide."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits pour effectuer cette action."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Compte temporairement verrouillé suite à plusieurs échecs. Réessayez plus tard."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Ressource introuvable."),
    CONFLICT(HttpStatus.CONFLICT, "Cette opération entre en conflit avec des données existantes."),
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY, "Opération refusée : une règle de gestion n'est pas respectée."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue. Réessayez ou contactez le responsable.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
