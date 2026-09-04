package com.lubrimanager.transaction.dto;

import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue d'une écriture. Utilisée aussi comme projection JPQL ({@code select new ...}) :
 * l'ordre et les types des composants doivent correspondre à la requête.
 */
public record TransactionResponse(
        Long id,
        String reference,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        Long accountId,
        Long customerId,
        String customerNo,
        String customerName,
        String cardNo,
        String operator,
        String description,
        Instant createdAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getReference(),
                t.getType(),
                t.getStatus(),
                t.getAmount(),
                t.getBalanceBefore(),
                t.getBalanceAfter(),
                t.getAccount().getId(),
                t.getCustomer().getId(),
                t.getCustomer().getCustomerNo(),
                t.getCustomer().getFullName(),
                t.getCard() != null ? t.getCard().getCardNo() : null,
                t.getUser().getUsername(),
                t.getDescription(),
                t.getCreatedAt());
    }
}
