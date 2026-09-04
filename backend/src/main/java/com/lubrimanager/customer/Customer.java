package com.lubrimanager.customer;

import com.lubrimanager.branch.Branch;
import com.lubrimanager.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Client de l'entreprise. Le {@code customerNo} est attribué par le backend et
 * immuable. Un client « utilisé » n'est jamais supprimé (RG-15) : on l'archive.
 *
 * <p>Le compte financier ({@code Account}) et les cartes seront rattachés en Phase 4.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer")
public class Customer extends BaseEntity {

    @Column(name = "customer_no", nullable = false, unique = true, updatable = false, length = 20)
    private String customerNo;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "company_name", length = 160)
    private String companyName;

    @Column(length = 30)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIF;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    public boolean isActive() {
        return status == CustomerStatus.ACTIF;
    }
}
