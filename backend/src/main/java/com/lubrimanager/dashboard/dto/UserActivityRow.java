package com.lubrimanager.dashboard.dto;

import java.math.BigDecimal;

public record UserActivityRow(Long userId, String username,
                              Long salesCount, BigDecimal salesAmount,
                              Long rechargeCount, BigDecimal rechargeAmount) {
}
