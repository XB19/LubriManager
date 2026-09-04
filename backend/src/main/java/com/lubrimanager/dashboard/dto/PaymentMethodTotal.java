package com.lubrimanager.dashboard.dto;

import java.math.BigDecimal;

public record PaymentMethodTotal(String paymentMethod, BigDecimal amount, Long count) {
}
