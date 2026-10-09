package com.lubrimanager.purchase.dto;

import com.lubrimanager.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Vue complète d'un débit carte (données du reçu). */
public record PurchaseResponse(
        Long id,
        String reference,
        Long transactionId,
        TransactionStatus status,
        Long accountId,
        Long customerId,
        String customerNo,
        String customerName,
        String cardNo,
        BigDecimal total,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String operator,
        String description,
        Instant createdAt,
        List<PurchaseItemResponse> items) {
}
