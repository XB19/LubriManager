package com.lubrimanager.purchase;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseCalculationTest {

    @Test
    void lineTotalIsUnitPriceTimesQuantity() {
        assertThat(PurchaseCalculation.lineTotal(new BigDecimal("8500"), new BigDecimal("3")))
                .isEqualByComparingTo("25500");
    }

    @Test
    void sumAddsAllAmounts() {
        assertThat(PurchaseCalculation.sum(List.of(
                new BigDecimal("1000"), new BigDecimal("2500"), new BigDecimal("500"))))
                .isEqualByComparingTo("4000");
    }

    @Test
    void sumOfEmptyIsZero() {
        assertThat(PurchaseCalculation.sum(List.of())).isEqualByComparingTo("0");
    }
}
