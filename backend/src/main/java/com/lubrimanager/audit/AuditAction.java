package com.lubrimanager.audit;

/**
 * Vocabulaire contrôlé des actions tracées (§25). Étendu au fil des phases.
 *
 * <p>Le journal d'audit est en ajout seul (RG : pas de suppression ni de
 * réécriture de l'historique) : une valeur ne doit jamais être retirée tant
 * que des lignes existantes peuvent la référencer, sous peine de rendre ces
 * lignes illisibles (l'action est stockée en base comme le nom de l'énum).
 * Si une fonctionnalité est retirée, sa valeur passe dans le bloc
 * « historique » ci-dessous au lieu d'être supprimée.
 */
public enum AuditAction {
    USER_LOGIN_SUCCESS,
    USER_LOGIN_FAILURE,
    USER_CREATED,
    USER_STATUS_CHANGED,
    CUSTOMER_STATUS_CHANGED,
    CARD_STATUS_CHANGED,
    ACCOUNT_ADJUSTED,
    RECHARGE_CREATED,
    PURCHASE_COMPLETED,
    PURCHASE_REFUSED,
    TRANSACTION_REVERSED,

    /** @deprecated historique (module stock retiré, Phase 11) — plus produite, conservée pour relire l'audit existant. */
    @Deprecated
    INVENTORY_MOVEMENT
}
