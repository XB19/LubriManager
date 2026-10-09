package com.lubrimanager.product.dto;

import com.lubrimanager.product.Product;
import java.math.BigDecimal;
import java.time.Instant;

/** Vue d'un produit. {@code unitPrice} est un montant unique en FCFA entiers. */
public record ProductResponse(
        Long id,
        String reference,
        String name,
        String brand,
        String unit,
        BigDecimal unitPrice,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getReference(),
                p.getName(),
                p.getBrand(),
                p.getUnit(),
                p.getUnitPrice(),
                p.getDescription(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
