package com.lubrimanager.inventory;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Calcul du delta et du stock résultant — logique pure. */
class InventoryCalculationTest {

    @Test
    void entryAddsToStock() {
        var r = InventoryCalculation.apply(InventoryMovementType.ENTREE, new BigDecimal("250"), new BigDecimal("100"));
        assertThat(r.delta()).isEqualByComparingTo("100");
        assertThat(r.stockAfter()).isEqualByComparingTo("350");
    }

    @Test
    void outflowSubtractsFromStock() {
        var r = InventoryCalculation.apply(InventoryMovementType.SORTIE, new BigDecimal("250"), new BigDecimal("10"));
        assertThat(r.delta()).isEqualByComparingTo("-10");
        assertThat(r.stockAfter()).isEqualByComparingTo("240");
    }

    @Test
    void adjustmentAppliesSignedDelta() {
        var down = InventoryCalculation.apply(InventoryMovementType.AJUSTEMENT, new BigDecimal("100"), new BigDecimal("-15"));
        assertThat(down.delta()).isEqualByComparingTo("-15");
        assertThat(down.stockAfter()).isEqualByComparingTo("85");
    }

    @Test
    void inventoryTakesCountedValueAsAbsoluteAndDerivesDelta() {
        var r = InventoryCalculation.apply(InventoryMovementType.INVENTAIRE, new BigDecimal("140"), new BigDecimal("135"));
        assertThat(r.stockAfter()).isEqualByComparingTo("135");
        assertThat(r.delta()).isEqualByComparingTo("-5");
    }
}
