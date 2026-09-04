package com.lubrimanager.recharge;

import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.payment.PaymentMethod;
import com.lubrimanager.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Détail métier d'un crédit de compte : moyen de paiement, commentaire, éventuel
 * motif de refus. Tout ce qui est financier (montant, soldes, statut, référence)
 * vit sur la {@link Transaction} associée (1–1).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "recharge")
public class Recharge extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(length = 500)
    private String comment;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;
}
