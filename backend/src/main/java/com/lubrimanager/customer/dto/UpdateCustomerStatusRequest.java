package com.lubrimanager.customer.dto;

import com.lubrimanager.customer.CustomerStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCustomerStatusRequest(
        @NotNull CustomerStatus status,
        @Size(max = 500) String reason) {
}
