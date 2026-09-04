package com.lubrimanager.branch;

import com.lubrimanager.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agence / point de vente. Introduite dès la Phase 2 (DEC-02) pour préparer le
 * multi-agences sans migration lourde ; une seule agence {@code MAIN} existe en v1.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "branch")
public class Branch extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
