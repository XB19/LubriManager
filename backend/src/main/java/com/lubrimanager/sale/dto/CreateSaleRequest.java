package com.lubrimanager.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSaleRequest(
        @NotNull Long cardId,
        @NotEmpty @Size(max = 100) @Valid List<SaleLineRequest> items,
        @Size(max = 500) String comment) {
}
