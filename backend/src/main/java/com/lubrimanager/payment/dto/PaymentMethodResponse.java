package com.lubrimanager.payment.dto;

import com.lubrimanager.payment.PaymentMethod;

public record PaymentMethodResponse(Long id, String code, String label, boolean active, int sortOrder) {

    public static PaymentMethodResponse from(PaymentMethod p) {
        return new PaymentMethodResponse(p.getId(), p.getCode(), p.getLabel(), p.isActive(), p.getSortOrder());
    }
}
