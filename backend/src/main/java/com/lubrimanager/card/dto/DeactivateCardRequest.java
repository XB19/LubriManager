package com.lubrimanager.card.dto;

import jakarta.validation.constraints.Size;

public record DeactivateCardRequest(@Size(max = 500) String reason) {
}
