package com.lubrimanager.customer.dto;

import com.lubrimanager.customer.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Le n° client et le statut ne se modifient pas ici (statut : endpoint dédié). */
public record UpdateCustomerRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 160) String companyName,
        @Size(max = 30) String phone,
        @Email @Size(max = 160) String email,
        @Size(max = 300) String address,
        @NotNull CustomerType type,
        @Size(max = 1000) String notes) {
}
