package com.lubrimanager.purchase.dto;

import com.lubrimanager.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Ligne de liste d'un débit carte. Projection JPQL : ordre = ordre de la requête. */
public record PurchaseSummaryResponse(
        Long id,
        String reference,
        TransactionStatus status,
        Long customerId,
        String customerNo,
        String cardNo,
        BigDecimal total,
        String operator,
        Instant createdAt) {
}
