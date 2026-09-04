package com.lubrimanager.customer.dto;

import com.lubrimanager.customer.Customer;
import com.lubrimanager.customer.CustomerStatus;
import com.lubrimanager.customer.CustomerType;
import java.time.Instant;

/**
 * Vue d'un client. En Phase 4+, une vue « fiche » enrichie ajoutera les cartes et
 * les agrégats financiers (solde total, total rechargé, total consommé).
 */
public record CustomerResponse(
        Long id,
        String customerNo,
        String fullName,
        String companyName,
        String phone,
        String email,
        String address,
        CustomerType type,
        CustomerStatus status,
        String notes,
        Long branchId,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getCustomerNo(),
                c.getFullName(),
                c.getCompanyName(),
                c.getPhone(),
                c.getEmail(),
                c.getAddress(),
                c.getType(),
                c.getStatus(),
                c.getNotes(),
                c.getBranch().getId(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
