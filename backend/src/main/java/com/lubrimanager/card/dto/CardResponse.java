package com.lubrimanager.card.dto;

import com.lubrimanager.card.Card;
import com.lubrimanager.card.CardStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CardResponse(
        Long id,
        String cardNo,
        String externalNo,
        Long customerId,
        String customerNo,
        String customerName,
        Long accountId,
        BigDecimal balance,
        CardStatus status,
        Instant issuedAt,
        Instant activatedAt,
        LocalDate expiresAt,
        Instant createdAt) {

    public static CardResponse from(Card c) {
        return new CardResponse(
                c.getId(),
                c.getCardNo(),
                c.getExternalNo(),
                c.getCustomer().getId(),
                c.getCustomer().getCustomerNo(),
                c.getCustomer().getFullName(),
                c.getAccount().getId(),
                c.getAccount().getBalance(),
                c.getStatus(),
                c.getIssuedAt(),
                c.getActivatedAt(),
                c.getExpiresAt(),
                c.getCreatedAt());
    }
}
