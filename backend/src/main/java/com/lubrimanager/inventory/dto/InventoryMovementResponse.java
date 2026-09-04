package com.lubrimanager.inventory.dto;

import com.lubrimanager.inventory.InventoryMovement;
import com.lubrimanager.inventory.InventoryMovementType;
import java.math.BigDecimal;
import java.time.Instant;

public record InventoryMovementResponse(
        Long id,
        Long productId,
        String productReference,
        String productName,
        InventoryMovementType type,
        BigDecimal quantity,
        BigDecimal stockBefore,
        BigDecimal stockAfter,
        String reference,
        String reason,
        String operator,
        Instant createdAt) {

    public static InventoryMovementResponse from(InventoryMovement m) {
        return new InventoryMovementResponse(
                m.getId(),
                m.getProduct().getId(),
                m.getProduct().getReference(),
                m.getProduct().getName(),
                m.getType(),
                m.getQuantity(),
                m.getStockBefore(),
                m.getStockAfter(),
                m.getReference(),
                m.getReason(),
                m.getUser().getUsername(),
                m.getCreatedAt());
    }
}
