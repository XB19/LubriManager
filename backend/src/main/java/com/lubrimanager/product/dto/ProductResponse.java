package com.lubrimanager.product.dto;

import com.lubrimanager.common.money.MoneyRules;
import com.lubrimanager.product.Product;
import com.lubrimanager.product.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue d'un produit. {@code unitPrice} est TTC ; {@code priceExclTax} et
 * {@code taxAmount} sont fournis pour information (DEC-04).
 */
public record ProductResponse(
        Long id,
        String reference,
        String name,
        String brand,
        Long categoryId,
        String categoryName,
        String description,
        String unit,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal priceExclTax,
        BigDecimal taxAmount,
        BigDecimal stockQuantity,
        BigDecimal alertThreshold,
        boolean lowStock,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getReference(),
                p.getName(),
                p.getBrand(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getDescription(),
                p.getUnit(),
                p.getUnitPrice(),
                p.getVatRate(),
                MoneyRules.exclTax(p.getUnitPrice(), p.getVatRate()),
                MoneyRules.taxOf(p.getUnitPrice(), p.getVatRate()),
                p.getStockQuantity(),
                p.getAlertThreshold(),
                p.isLowStock(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
