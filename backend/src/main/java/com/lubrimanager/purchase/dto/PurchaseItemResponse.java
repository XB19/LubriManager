package com.lubrimanager.purchase.dto;

import com.lubrimanager.purchase.PurchaseItem;
import java.math.BigDecimal;

public record PurchaseItemResponse(
        Long productId,
        String productReference,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {

    public static PurchaseItemResponse from(PurchaseItem i) {
        return new PurchaseItemResponse(
                i.getProduct().getId(),
                i.getProductReference(),
                i.getProductName(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getLineTotal());
    }
}
