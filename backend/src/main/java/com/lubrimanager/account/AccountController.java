package com.lubrimanager.account;

import com.lubrimanager.account.dto.AccountResponse;
import com.lubrimanager.account.dto.AdjustAccountRequest;
import com.lubrimanager.common.Actor;
import com.lubrimanager.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Comptes")
@RestController
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private final AccountService accountService;
    private final AccountAdjustmentService adjustmentService;

    public AccountController(AccountService accountService, AccountAdjustmentService adjustmentService) {
        this.accountService = accountService;
        this.adjustmentService = adjustmentService;
    }

    @GetMapping("/api/accounts/{id}")
    @Operation(summary = "Consulter un compte (solde, devise, statut)")
    public AccountResponse get(@PathVariable Long id) {
        return AccountResponse.from(accountService.get(id));
    }

    @GetMapping("/api/customers/{customerId}/account")
    @Operation(summary = "Compte d'un client")
    public AccountResponse getByCustomer(@PathVariable Long customerId) {
        return AccountResponse.from(accountService.getByCustomer(customerId));
    }

    @PostMapping("/api/accounts/{id}/adjustment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajustement manuel de solde (motif obligatoire, tracé)")
    public AccountResponse adjust(@PathVariable Long id, @Valid @RequestBody AdjustAccountRequest request,
                                  @AuthenticationPrincipal AppUserDetails current) {
        Actor actor = new Actor(current.getId(), current.getUsername(), current.getBranchId());
        return AccountResponse.from(adjustmentService.adjust(id, request.amount(), request.reason(), actor));
    }
}
