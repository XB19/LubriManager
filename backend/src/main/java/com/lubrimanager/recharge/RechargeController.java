package com.lubrimanager.recharge;

import com.lubrimanager.common.Actor;
import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.recharge.dto.CreateRechargeRequest;
import com.lubrimanager.recharge.dto.RechargeResponse;
import com.lubrimanager.recharge.dto.RejectRechargeRequest;
import com.lubrimanager.security.AppUserDetails;
import com.lubrimanager.transaction.TransactionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recharges")
@RestController
@RequestMapping("/api/recharges")
@PreAuthorize("isAuthenticated()")
public class RechargeController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final RechargeService rechargeService;

    public RechargeController(RechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    @Operation(summary = "Effectuer une recharge (en-tête Idempotency-Key obligatoire)")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
            description = "UUID unique de l'opération, figé jusqu'au succès")
    public RechargeResponse create(@Valid @RequestBody CreateRechargeRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @AuthenticationPrincipal AppUserDetails current) {
        return rechargeService.createRecharge(request, idempotencyKey, actor(current));
    }

    @GetMapping
    @Operation(summary = "Lister / filtrer les recharges")
    public PageResponse<RechargeResponse> list(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long cardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : FAR_FUTURE;
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<RechargeResponse> result = rechargeService.search(status, accountId, customerId, cardId,
                fromInstant, toInstant,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une recharge")
    public RechargeResponse get(@PathVariable Long id) {
        return rechargeService.get(id);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Valider une recharge en attente")
    public RechargeResponse validate(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails current) {
        return rechargeService.validate(id, actor(current));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Refuser une recharge en attente (motif obligatoire)")
    public RechargeResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRechargeRequest request,
                                   @AuthenticationPrincipal AppUserDetails current) {
        return rechargeService.reject(id, actor(current), request.reason());
    }

    private static Actor actor(AppUserDetails current) {
        return new Actor(current.getId(), current.getUsername(), current.getBranchId());
    }
}
