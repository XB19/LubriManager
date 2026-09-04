package com.lubrimanager.audit;

/** Vocabulaire contrôlé des actions tracées (§25). Étendu au fil des phases. */
public enum AuditAction {
    USER_LOGIN_SUCCESS,
    USER_LOGIN_FAILURE,
    USER_CREATED,
    USER_STATUS_CHANGED,
    CUSTOMER_STATUS_CHANGED,
    CARD_STATUS_CHANGED,
    ACCOUNT_ADJUSTED,
    RECHARGE_CREATED,
    RECHARGE_VALIDATED,
    RECHARGE_REJECTED,
    SALE_COMPLETED,
    SALE_REFUSED,
    TRANSACTION_REVERSED,
    INVENTORY_MOVEMENT
}
