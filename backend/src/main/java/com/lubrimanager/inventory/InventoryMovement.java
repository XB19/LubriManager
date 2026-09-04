package com.lubrimanager.inventory;

import com.lubrimanager.branch.Branch;
import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.product.Product;
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
 * Mouvement de stock — <strong>en ajout seul</strong>. {@code quantity} est le
 * delta signé appliqué ; {@code stockBefore}/{@code stockAfter} figent l'état de
 * part et d'autre. Invariant : {@code product.stockQuantity == Σ quantity}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "inventory_movement")
public class InventoryMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryMovementType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity;

    @Column(name = "stock_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal stockBefore;

    @Column(name = "stock_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal stockAfter;

    @Column(length = 60)
    private String reference;

    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
}
