package com.lubrimanager.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Le stock ne se modifie pas ici : il évolue via les mouvements de stock (Phase 7). */
public record UpdateProductRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 100) String brand,
        @NotNull Long categoryId,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal vatRate,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal alertThreshold) {
}
