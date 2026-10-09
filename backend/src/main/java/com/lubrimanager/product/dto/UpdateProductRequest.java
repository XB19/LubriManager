package com.lubrimanager.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 100) String brand,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
        @Size(max = 1000) String description) {
}
