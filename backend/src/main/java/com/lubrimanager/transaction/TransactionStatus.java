package com.lubrimanager.transaction;

/** État d'une écriture (§22). Seule une écriture VALIDEE participe au solde (RG-00). */
public enum TransactionStatus {
    EN_ATTENTE,
    VALIDEE,
    REFUSEE,
    ANNULEE
}
