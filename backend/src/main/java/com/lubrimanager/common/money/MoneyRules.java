package com.lubrimanager.common.money;

import java.math.BigDecimal;

/**
 * Règles monétaires transverses. Le FCFA (XOF) n'a pas de sous-unité : tous les
 * montants sont des entiers, stockés en {@code NUMERIC(15,2)} avec une partie
 * décimale nulle (contrainte {@code CHECK} en base).
 */
public final class MoneyRules {

    public static final String CURRENCY = "XOF";

    private MoneyRules() {
    }

    public static boolean isWhole(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() <= 0;
    }

    public static boolean isStrictlyPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    /** Ramène à l'échelle 2 pour le stockage (échoue si des décimales non nulles existent). */
    public static BigDecimal scaled(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    /**
     * Montant hors taxe d'un prix TTC (DEC-04 : les prix sont saisis TTC).
     * {@code ratePercent} en pourcentage (ex. {@code 18.00}). Résultat informatif à 2 décimales.
     */
    public static BigDecimal exclTax(BigDecimal ttc, BigDecimal ratePercent) {
        if (ttc == null) {
            return null;
        }
        BigDecimal divisor = BigDecimal.ONE.add(ratePercent.movePointLeft(2));
        return ttc.divide(divisor, 2, java.math.RoundingMode.HALF_UP);
    }

    /** Part de TVA contenue dans un prix TTC. */
    public static BigDecimal taxOf(BigDecimal ttc, BigDecimal ratePercent) {
        if (ttc == null) {
            return null;
        }
        return ttc.subtract(exclTax(ttc, ratePercent));
    }
}
