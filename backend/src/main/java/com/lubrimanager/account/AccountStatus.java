package com.lubrimanager.account;

/**
 * État d'un compte financier. En v1, un compte reste {@link #ACTIF} ; {@link #GELE}
 * et {@link #CLOS} sont prévus pour des phases ultérieures.
 */
public enum AccountStatus {
    ACTIF,
    GELE,
    CLOS
}
