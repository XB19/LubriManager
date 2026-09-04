package com.lubrimanager.customer;

import com.lubrimanager.common.Actor;
import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.customer.dto.CreateCustomerRequest;
import com.lubrimanager.customer.dto.CustomerResponse;
import com.lubrimanager.customer.dto.UpdateCustomerRequest;
import com.lubrimanager.customer.dto.UpdateCustomerStatusRequest;
import com.lubrimanager.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * API clients. Lecture ouverte à tout utilisateur authentifié ; création /
 * modification pour ADMIN, MANAGER, AGENT ; changement de statut réservé à
 * ADMIN et MANAGER (matrice RBAC §10).
 */
@Tag(name = "Clients")
@RestController
@RequestMapping("/api/customers")
@PreAuthorize("isAuthenticated()")
public class CustomerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "Rechercher / lister les clients")
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) CustomerType type,
                                               @RequestParam(required = false) CustomerStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Customer> result = customerService.search(q, type, status,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("fullName").ascending()));
        return PageResponse.of(result, CustomerResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche d'un client")
    public CustomerResponse get(@PathVariable Long id) {
        return CustomerResponse.from(customerService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    @Operation(summary = "Créer un client (le n° client est attribué automatiquement)")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        Customer created = customerService.create(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/customers/{id}").buildAndExpand(created.getId()).toUri())
                .body(CustomerResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    @Operation(summary = "Modifier les informations d'un client")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCustomerRequest request) {
        return CustomerResponse.from(customerService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Changer le statut d'un client (actif, suspendu, bloqué, archivé)")
    public CustomerResponse updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody UpdateCustomerStatusRequest request,
                                         @AuthenticationPrincipal AppUserDetails current) {
        Actor actor = new Actor(current.getId(), current.getUsername(), current.getBranchId());
        return CustomerResponse.from(customerService.updateStatus(id, request, actor));
    }
}
