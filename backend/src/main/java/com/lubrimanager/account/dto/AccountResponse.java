package com.lubrimanager.account.dto;

import com.lubrimanager.account.Account;
import com.lubrimanager.account.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue d'un compte. Les agrégats (total rechargé / consommé, dernière opération)
 * seront ajoutés en Phase 5+ quand les transactions existeront.
 */
public record AccountResponse(
        Long id,
        Long customerId,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getId(),
                a.getCustomer().getId(),
                a.getBalance(),
                a.getCurrency(),
                a.getStatus(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
