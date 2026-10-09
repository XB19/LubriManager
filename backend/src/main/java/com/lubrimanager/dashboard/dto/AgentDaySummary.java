package com.lubrimanager.dashboard.dto;

import java.math.BigDecimal;

/** Vue « rapport de caisse » de l'agent connecté (DEC-20). */
public record AgentDaySummary(
        String operator,
        long salesCount,
        BigDecimal salesAmount,
        long rechargeCount,
        BigDecimal rechargeAmount) {
}
