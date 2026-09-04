package com.lubrimanager.payment;

import com.lubrimanager.payment.dto.CreatePaymentMethodRequest;
import com.lubrimanager.payment.dto.PaymentMethodResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Moyens de paiement")
@RestController
@RequestMapping("/api/payment-methods")
@PreAuthorize("isAuthenticated()")
public class PaymentMethodController {

    private final PaymentMethodService service;

    public PaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lister les moyens de paiement")
    public List<PaymentMethodResponse> list(@RequestParam(defaultValue = "true") boolean onlyActive) {
        return service.list(onlyActive).stream().map(PaymentMethodResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajouter un moyen de paiement")
    public PaymentMethodResponse create(@Valid @RequestBody CreatePaymentMethodRequest request) {
        return PaymentMethodResponse.from(service.create(request));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer / désactiver un moyen de paiement")
    public PaymentMethodResponse setActive(@PathVariable Long id, @RequestParam boolean value) {
        return PaymentMethodResponse.from(service.setActive(id, value));
    }
}
