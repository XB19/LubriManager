package com.lubrimanager.purchase;

import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * En-tête d'un débit carte : achat d'un ou plusieurs produits réglé sur le solde
 * du compte. Le financier (référence, statut, montant signé, soldes) vit sur la
 * {@link Transaction} de type ACHAT associée (1–1).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "card_purchase")
public class Purchase extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;
}
