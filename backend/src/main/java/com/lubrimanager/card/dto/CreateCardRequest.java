package com.lubrimanager.card.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateCardRequest(
        @NotNull Long customerId,
        @Size(max = 40) String externalNo,
        @FutureOrPresent LocalDate expiresAt,
        boolean activateImmediately) {
}
