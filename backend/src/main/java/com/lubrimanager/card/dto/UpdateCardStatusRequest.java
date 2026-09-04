package com.lubrimanager.card.dto;

import com.lubrimanager.card.CardStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cible autorisée : ACTIVE, SUSPENDUE, BLOQUEE, PERDUE (contrôle dans le service). */
public record UpdateCardStatusRequest(
        @NotNull CardStatus status,
        @Size(max = 500) String reason) {
}
