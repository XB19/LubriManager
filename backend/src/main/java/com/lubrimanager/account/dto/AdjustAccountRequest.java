package com.lubrimanager.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** {@code amount} signé (positif = crédit, négatif = débit), entier FCFA, non nul. */
public record AdjustAccountRequest(
        @NotNull @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 500) String reason) {
}
