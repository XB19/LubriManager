package com.lubrimanager.recharge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRechargeRequest(@NotBlank @Size(max = 500) String reason) {
}
