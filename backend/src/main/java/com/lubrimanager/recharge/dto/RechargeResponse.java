package com.lubrimanager.recharge.dto;

import com.lubrimanager.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue d'une recharge (données du reçu, §13). Aussi utilisée comme projection JPQL
 * pour la liste : ordre et types des composants = ordre de la requête.
 *
 * <p>{@code status = EN_ATTENTE} ⇒ recharge en attente de validation, solde non
 * encore modifié ({@code balanceAfter} est alors une estimation).
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
        String paymentMethod,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String operator,
        String comment,
        Instant createdAt) {
}
