package com.lubrimanager.recharge.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateRechargeRequest(
        @NotNull Long customerId,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Size(max = 500) String comment) {
}
