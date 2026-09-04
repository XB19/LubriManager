package com.lubrimanager.customer;

/**
 * Nature du client (DEC-11). Valeurs par défaut retenues faute d'arbitrage métier ;
 * ajustables via une migration + cette énumération.
 */
public enum CustomerType {
    PARTICULIER,
    ENTREPRISE,
    ADMINISTRATION
}
