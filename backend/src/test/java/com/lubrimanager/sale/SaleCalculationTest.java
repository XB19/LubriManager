package com.lubrimanager.sale;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaleCalculationTest {

    @Test
    void lineTotalIsPriceTimesQuantity() {
        assertThat(SaleCalculation.lineTotal(new BigDecimal("8500"), new BigDecimal("10")))
                .isEqualByComparingTo("85000");
    }

    @Test
    void sumAddsAllAmounts() {
        assertThat(SaleCalculation.sum(List.of(
                new BigDecimal("85000"), new BigDecimal("17000"), new BigDecimal("3000"))))
                .isEqualByComparingTo("105000");
        assertThat(SaleCalculation.sum(List.of())).isEqualByComparingTo("0");
    }
}
