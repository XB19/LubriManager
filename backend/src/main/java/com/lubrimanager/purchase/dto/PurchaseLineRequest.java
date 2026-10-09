package com.lubrimanager.purchase.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PurchaseLineRequest(
        @NotNull Long productId,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal quantity) {
}
