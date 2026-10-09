package com.lubrimanager.recharge.dto;

import com.lubrimanager.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue d'une recharge (données du reçu). Aussi utilisée comme projection JPQL pour
 * la liste : ordre et types des composants = ordre de la requête.
 */
public record RechargeResponse(
        Long id,
        String reference,
        Long transactionId,
        TransactionStatus status,
        Long accountId,
        Long customerId,
        String customerNo,
        String cardNo,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String operator,
        String comment,
        Instant createdAt) {
}
