package com.lubrimanager.sale.dto;

import com.lubrimanager.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Vue complète d'une vente (données du reçu / de la facture, §17). */
public record SaleResponse(
        Long id,
        String reference,
        Long transactionId,
        TransactionStatus status,
        Long accountId,
        Long customerId,
        String customerNo,
        String customerName,
        String cardNo,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String operator,
        String description,
        Instant createdAt,
        List<SaleItemResponse> items) {
}
