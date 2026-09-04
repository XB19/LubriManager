package com.lubrimanager.sale;

import com.lubrimanager.common.domain.BaseEntity;
import com.lubrimanager.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligne de vente. Le nom, la référence, le prix unitaire et le taux de TVA du
 * produit sont <strong>figés</strong> au moment de la vente (RG-07) afin que la
 * réimpression d'un reçu reste fidèle même si le produit change ensuite.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sale_item")
public class SaleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Column(name = "product_reference", nullable = false, length = 20)
    private String productReference;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;
}
