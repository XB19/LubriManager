package com.lubrimanager.card;

import org.junit.jupiter.api.Test;

import static com.lubrimanager.card.CardStatus.ACTIVE;
import static com.lubrimanager.card.CardStatus.BLOQUEE;
import static com.lubrimanager.card.CardStatus.DESACTIVEE;
import static com.lubrimanager.card.CardStatus.EN_ATTENTE;
import static com.lubrimanager.card.CardStatus.EXPIREE;
import static com.lubrimanager.card.CardStatus.PERDUE;
import static com.lubrimanager.card.CardStatus.SUSPENDUE;
import static org.assertj.core.api.Assertions.assertThat;

/** Machine à états des cartes — logique pure. */
class CardStatusTest {

    @Test
    void allowsExpectedForwardTransitions() {
        assertThat(EN_ATTENTE.canTransitionTo(ACTIVE)).isTrue();
        assertThat(ACTIVE.canTransitionTo(SUSPENDUE)).isTrue();
        assertThat(SUSPENDUE.canTransitionTo(ACTIVE)).isTrue();
        assertThat(BLOQUEE.canTransitionTo(ACTIVE)).isTrue();
        assertThat(ACTIVE.canTransitionTo(EXPIREE)).isTrue();
    }

    @Test
    void forbidsResurrectionFromTerminalOrLostStates() {
        assertThat(PERDUE.canTransitionTo(ACTIVE)).isFalse();
        assertThat(EXPIREE.canTransitionTo(ACTIVE)).isFalse();
        for (CardStatus s : CardStatus.values()) {
            assertThat(DESACTIVEE.canTransitionTo(s)).isFalse();
        }
    }

    @Test
    void everyNonTerminalStateCanBeDeactivated() {
        for (CardStatus s : CardStatus.values()) {
            if (s != DESACTIVEE) {
                assertThat(s.canTransitionTo(DESACTIVEE)).as("%s -> DESACTIVEE", s).isTrue();
            }
        }
    }

    @Test
    void operatorSettableExcludesTerminalAndBatchStates() {
        assertThat(CardStatus.OPERATOR_SETTABLE)
                .containsExactlyInAnyOrder(ACTIVE, SUSPENDUE, BLOQUEE, PERDUE)
                .doesNotContain(DESACTIVEE, EXPIREE, EN_ATTENTE);
    }
}
