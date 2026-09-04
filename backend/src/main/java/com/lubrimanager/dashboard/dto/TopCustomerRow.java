package com.lubrimanager.dashboard.dto;

import java.math.BigDecimal;

public record TopCustomerRow(Long customerId, String customerNo, String name,
                             BigDecimal purchaseTotal, Long purchaseCount) {
}
