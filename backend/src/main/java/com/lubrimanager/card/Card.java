package com.lubrimanager.card;

import com.lubrimanager.account.Account;
import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carte d'accès au compte d'un client. Un client peut détenir plusieurs cartes
 * actives sur son unique compte (DEC-24). Le {@code cardNo} est attribué par le
 * backend (DEC-09) ; {@code externalNo} accueille en option un identifiant
 * physique déjà existant (badge, QR).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "card")
public class Card extends BaseEntity {

    @Column(name = "card_no", nullable = false, unique = true, updatable = false, length = 20)
    private String cardNo;

    @Column(name = "external_no", unique = true, length = 40)
    private String externalNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status = CardStatus.EN_ATTENTE;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    /** Optionnel (DEC-23). Date pleine, sans heure. */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    public boolean isExpired(LocalDate today) {
        return expiresAt != null && expiresAt.isBefore(today);
    }
}
