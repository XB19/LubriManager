package com.lubrimanager.sale;

import java.math.BigDecimal;
import java.util.Collection;

/** Calculs de vente — logique pure. Prix TTC (DEC-04) : {@code total == subtotal}. */
public final class SaleCalculation {

    private SaleCalculation() {
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, BigDecimal quantity) {
        return unitPrice.multiply(quantity);
    }

    public static BigDecimal sum(Collection<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
