package com.lubrimanager.product;

import com.lubrimanager.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Article du catalogue. Le {@code reference} est attribué par le backend.
 * {@code unitPrice} est le prix en FCFA entiers. Modèle volontairement minimal :
 * nom, marque, unité (texte libre), prix, description facultative.
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

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(length = 1000)
    private String description;
}
