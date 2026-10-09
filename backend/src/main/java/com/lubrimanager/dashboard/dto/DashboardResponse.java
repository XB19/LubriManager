package com.lubrimanager.dashboard.dto;

import java.math.BigDecimal;

/** Indicateurs du tableau de bord (§29). */
public record DashboardResponse(Counts counts, Period today, Period week, Period month) {

    public record Counts(
            long customers,
            long activeCustomers,
            long totalCards,
            long activeCards,
            long blockedCards) {
    }

    /** {@code salesAmount} = total des débits carte sur la période. */
    public record Period(
            BigDecimal rechargeAmount,
            long rechargeCount,
            BigDecimal salesAmount,
            long salesCount) {
    }
}
