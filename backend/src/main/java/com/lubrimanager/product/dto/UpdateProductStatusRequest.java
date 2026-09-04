package com.lubrimanager.product.dto;

import com.lubrimanager.product.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(@NotNull ProductStatus status) {
}
