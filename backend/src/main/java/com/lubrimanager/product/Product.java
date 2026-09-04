package com.lubrimanager.product;

import com.lubrimanager.category.Category;
import com.lubrimanager.common.domain.BaseEntity;
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
 * Article du catalogue. Le {@code reference} est attribué par le backend.
 * {@code unitPrice} est le prix <strong>TTC</strong> en FCFA entiers (DEC-04) ;
 * {@code vatRate} sert au détail de TVA sur les reçus / factures (Phase 8+).
 *
 * <p>{@code stockQuantity} est un cache : la vérité est la suite des mouvements
 * de stock (Phase 7). En Phase 6 il vaut 0 et n'est pas modifiable par l'API.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "product")
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String reference;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 100)
    private String brand;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = new BigDecimal("18.00");

    @Column(name = "stock_quantity", nullable = false, precision = 15, scale = 2)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Column(name = "alert_threshold", nullable = false, precision = 15, scale = 2)
    private BigDecimal alertThreshold = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIF;

    public boolean isLowStock() {
        return stockQuantity.compareTo(alertThreshold) <= 0;
    }

    public boolean isSellable() {
        return status == ProductStatus.ACTIF;
    }
}
