package com.lubrimanager.payment;

import com.lubrimanager.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Moyen de paiement d'une recharge (§15). Configurable : on peut en désactiver
 * ou en ajouter sans redéploiement.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "payment_method")
public class PaymentMethod extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
