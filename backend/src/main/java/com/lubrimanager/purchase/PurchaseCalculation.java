package com.lubrimanager.purchase;

import java.math.BigDecimal;
import java.util.Collection;

/** Calculs d'un débit carte — logique pure, sans TVA. */
public final class PurchaseCalculation {

    private PurchaseCalculation() {
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, BigDecimal quantity) {
        return unitPrice.multiply(quantity);
    }

    public static BigDecimal sum(Collection<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
