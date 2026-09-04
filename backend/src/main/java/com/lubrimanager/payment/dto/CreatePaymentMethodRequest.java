package com.lubrimanager.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePaymentMethodRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9_]+$", message = "Majuscules, chiffres et _ uniquement")
        String code,
        @NotBlank @Size(max = 80) String label,
        Integer sortOrder) {
}
