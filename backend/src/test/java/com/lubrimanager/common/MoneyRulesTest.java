package com.lubrimanager.common;

import com.lubrimanager.common.money.MoneyRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyRulesTest {

    @Test
    void wholeAmountsAreAccepted() {
        assertThat(MoneyRules.isWhole(new BigDecimal("100"))).isTrue();
        assertThat(MoneyRules.isWhole(new BigDecimal("100.00"))).isTrue();
        assertThat(MoneyRules.isWhole(new BigDecimal("250000.0000"))).isTrue();
    }

    @Test
    void fractionalAmountsAreRejected() {
        assertThat(MoneyRules.isWhole(new BigDecimal("100.50"))).isFalse();
        assertThat(MoneyRules.isWhole(new BigDecimal("0.01"))).isFalse();
    }

    @Test
    void strictlyPositiveIsEnforced() {
        assertThat(MoneyRules.isStrictlyPositive(new BigDecimal("1"))).isTrue();
        assertThat(MoneyRules.isStrictlyPositive(BigDecimal.ZERO)).isFalse();
        assertThat(MoneyRules.isStrictlyPositive(new BigDecimal("-5"))).isFalse();
        assertThat(MoneyRules.isStrictlyPositive(null)).isFalse();
    }

    @Test
    void scaledNormalizesToTwoDecimals() {
        assertThat(MoneyRules.scaled(new BigDecimal("100"))).isEqualByComparingTo("100.00");
        assertThat(MoneyRules.scaled(new BigDecimal("100")).scale()).isEqualTo(2);
    }
}
