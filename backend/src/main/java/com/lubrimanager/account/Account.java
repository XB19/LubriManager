package com.lubrimanager.account;

import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Portefeuille prépayé d'un client (DEC-01 : exactement un compte par client).
 * La carte n'est qu'un moyen d'accès à ce compte.
 *
 * <p>Le solde ne bouge que par une transaction validée (Phase 5+). Ici il vaut 0
 * à l'ouverture. DEC-08 : jamais de découvert — contrainte {@code balance >= 0} en base.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "account")
public class Account extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "XOF";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIF;

    public boolean isActive() {
        return status == AccountStatus.ACTIF;
    }
}
