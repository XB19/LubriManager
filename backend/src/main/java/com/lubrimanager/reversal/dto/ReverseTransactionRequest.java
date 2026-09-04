package com.lubrimanager.reversal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseTransactionRequest(@NotBlank @Size(max = 500) String reason) {
}
