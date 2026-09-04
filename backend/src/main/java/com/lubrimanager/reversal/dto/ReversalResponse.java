package com.lubrimanager.reversal.dto;

import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record ReversalResponse(
        Long reversalId,
        String reversalReference,
        BigDecimal reversalAmount,
        Long originalId,
        String originalReference,
        TransactionType originalType,
        TransactionStatus originalStatus,
        Long accountId,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String reason,
        String operator,
        Instant createdAt) {

    public static ReversalResponse of(Transaction reversal, Transaction original, String operator) {
        return new ReversalResponse(
                reversal.getId(),
                reversal.getReference(),
                reversal.getAmount(),
                original.getId(),
                original.getReference(),
                original.getType(),
                original.getStatus(),
                reversal.getAccount().getId(),
                reversal.getBalanceBefore(),
                reversal.getBalanceAfter(),
                reversal.getDescription(),
                operator,
                reversal.getCreatedAt());
    }
}
