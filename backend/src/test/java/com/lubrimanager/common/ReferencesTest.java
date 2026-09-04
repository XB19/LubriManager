package com.lubrimanager.common;

import com.lubrimanager.common.reference.References;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Formatage des références — logique pure. */
class ReferencesTest {

    @Test
    void formatsSequentialReferenceZeroPadded() {
        assertThat(References.format("CLI", 1, 6)).isEqualTo("CLI-000001");
        assertThat(References.format("CLI", 42, 6)).isEqualTo("CLI-000042");
        assertThat(References.format("CLI", 1_234_567, 6)).isEqualTo("CLI-1234567");
    }

    @Test
    void formatsDatedReference() {
        assertThat(References.formatDated("RECH", LocalDate.of(2026, 9, 3), 1, 6))
                .isEqualTo("RECH-20260903-000001");
    }

    @Test
    void datedScopeIsPrefixPlusDay() {
        assertThat(References.datedScope("RECH", LocalDate.of(2026, 9, 3))).isEqualTo("RECH-20260903");
    }

    @Test
    void rejectsNegativeSequence() {
        assertThatThrownBy(() -> References.format("CLI", -1, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
