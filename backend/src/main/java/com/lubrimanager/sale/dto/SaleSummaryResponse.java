package com.lubrimanager.sale.dto;

import com.lubrimanager.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Ligne de liste des ventes (projection JPQL — ordre/types alignés sur la requête). */
public record SaleSummaryResponse(
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
