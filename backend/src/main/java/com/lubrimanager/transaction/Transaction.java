package com.lubrimanager.transaction;

import com.lubrimanager.account.Account;
import com.lubrimanager.branch.Branch;
import com.lubrimanager.card.Card;
import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.customer.Customer;
import com.lubrimanager.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Écriture au grand livre — <strong>en ajout seul</strong>. Aucune suppression ;
 * les seules modifications admises sont les transitions de {@code status}
 * (EN_ATTENTE → VALIDEE / REFUSEE / ANNULEE). Le montant est signé
 * (+ crédit, − débit). {@code balanceBefore}/{@code balanceAfter} figent l'état
 * du solde de part et d'autre de l'écriture (RG-16).
 *
 * <p>Table {@code account_transaction} ({@code transaction} est un mot-clé SQL).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "account_transaction")
public class Transaction extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_transaction_id")
    private Transaction relatedTransaction;

    @Column(name = "idempotency_key", unique = true, length = 80)
    private String idempotencyKey;

    @Column(length = 500)
    private String description;

    public boolean isPending() {
        return status == TransactionStatus.EN_ATTENTE;
    }
}
