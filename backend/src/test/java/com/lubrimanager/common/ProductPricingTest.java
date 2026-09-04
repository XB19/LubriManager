package com.lubrimanager.common;

import com.lubrimanager.common.money.MoneyRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Décomposition TTC → HT + TVA (DEC-04). */
class ProductPricingTest {

    @Test
    void splitsInclusivePriceIntoNetAndTax() {
        BigDecimal ttc = new BigDecimal("8500");
        BigDecimal rate = new BigDecimal("18.00");

        BigDecimal net = MoneyRules.exclTax(ttc, rate);
        BigDecimal tax = MoneyRules.taxOf(ttc, rate);

        assertThat(net).isEqualByComparingTo("7203.39");
        assertThat(tax).isEqualByComparingTo("1296.61");
        assertThat(net.add(tax)).isEqualByComparingTo(ttc);
    }

    @Test
    void zeroRateLeavesPriceUntouched() {
        BigDecimal ttc = new BigDecimal("12000");
        assertThat(MoneyRules.exclTax(ttc, BigDecimal.ZERO)).isEqualByComparingTo("12000");
        assertThat(MoneyRules.taxOf(ttc, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void nullPriceYieldsNull() {
        assertThat(MoneyRules.exclTax(null, new BigDecimal("18.00"))).isNull();
        assertThat(MoneyRules.taxOf(null, new BigDecimal("18.00"))).isNull();
    }
}
