package com.lubrimanager.common.idempotency;

public enum IdempotencyStatus {
    /** Opération en cours de traitement (clé réservée, réponse pas encore connue). */
    IN_PROGRESS,
    /** Opération terminée : la réponse mémorisée peut être rejouée. */
    COMPLETED
}
