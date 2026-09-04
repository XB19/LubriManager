package com.lubrimanager.transaction;

/** Nature d'une écriture au grand livre (§21). */
public enum TransactionType {
    RECHARGE,
    ACHAT,
    REMBOURSEMENT,
    ANNULATION,
    AJUSTEMENT,
    CORRECTION
}
