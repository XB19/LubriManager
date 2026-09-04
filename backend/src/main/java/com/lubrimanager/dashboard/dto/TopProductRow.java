package com.lubrimanager.dashboard.dto;

import java.math.BigDecimal;

public record TopProductRow(Long productId, String reference, String name,
                            BigDecimal quantitySold, BigDecimal revenue) {
}
