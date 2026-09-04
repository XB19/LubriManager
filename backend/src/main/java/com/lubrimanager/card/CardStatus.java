package com.lubrimanager.card;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cycle de vie d'une carte.
 *
 * <pre>
 * EN_ATTENTE ──activation──▶ ACTIVE ⇄ SUSPENDUE
 *      │                       │  │
 *      ├───────────────────────┼──┼──▶ BLOQUEE ──▶ ACTIVE
 *      └───────────────────────┴──┴──▶ PERDUE ───▶ DESACTIVEE
 *                              └──────▶ EXPIREE ──▶ DESACTIVEE   (batch quotidien, RG-18)
 * DESACTIVEE = état terminal (remplacement = nouvelle carte).
 * </pre>
 */
public enum CardStatus {

    EN_ATTENTE,
    ACTIVE,
    SUSPENDUE,
    BLOQUEE,
    PERDUE,
    EXPIREE,
    DESACTIVEE;

    private static final Map<CardStatus, Set<CardStatus>> ALLOWED = Map.of(
            EN_ATTENTE, EnumSet.of(ACTIVE, BLOQUEE, PERDUE, DESACTIVEE),
            ACTIVE, EnumSet.of(SUSPENDUE, BLOQUEE, PERDUE, EXPIREE, DESACTIVEE),
            SUSPENDUE, EnumSet.of(ACTIVE, BLOQUEE, PERDUE, EXPIREE, DESACTIVEE),
            BLOQUEE, EnumSet.of(ACTIVE, PERDUE, DESACTIVEE),
            PERDUE, EnumSet.of(DESACTIVEE),
            EXPIREE, EnumSet.of(DESACTIVEE),
            DESACTIVEE, EnumSet.noneOf(CardStatus.class));

    /** Statuts que l'opérateur peut poser via l'endpoint de changement de statut. */
    public static final Set<CardStatus> OPERATOR_SETTABLE =
            EnumSet.of(ACTIVE, SUSPENDUE, BLOQUEE, PERDUE);

    public boolean canTransitionTo(CardStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isUsable() {
        return this == ACTIVE;
    }
}
