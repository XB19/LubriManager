package com.lubrimanager.recharge;

import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Détail métier d'un crédit de compte (paiement en espèces, commentaire libre).
 * Tout ce qui est financier (montant, soldes, statut, référence) vit sur la
 * {@link Transaction} associée (1–1).
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

    @Column(length = 500)
    private String comment;
}
