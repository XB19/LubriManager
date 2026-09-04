package com.lubrimanager.sale.dto;

import com.lubrimanager.sale.SaleItem;
import java.math.BigDecimal;

public record SaleItemResponse(
        Long productId,
        String productReference,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal lineTotal) {

    public static SaleItemResponse from(SaleItem i) {
        return new SaleItemResponse(
                i.getProduct().getId(),
                i.getProductReference(),
                i.getProductName(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getVatRate(),
                i.getLineTotal());
    }
}
