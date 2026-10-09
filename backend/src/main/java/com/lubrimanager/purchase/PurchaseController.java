package com.lubrimanager.purchase;

import com.lubrimanager.common.Actor;
import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.purchase.dto.CreatePurchaseRequest;
import com.lubrimanager.purchase.dto.PurchaseResponse;
import com.lubrimanager.purchase.dto.PurchaseSummaryResponse;
import com.lubrimanager.security.AppUserDetails;
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

@Tag(name = "Débits carte")
@RestController
@RequestMapping("/api/purchases")
@PreAuthorize("isAuthenticated()")
public class PurchaseController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    @Operation(summary = "Débiter une carte pour un ou plusieurs produits (en-tête Idempotency-Key obligatoire)")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true)
    public PurchaseResponse create(@Valid @RequestBody CreatePurchaseRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @AuthenticationPrincipal AppUserDetails current) {
        return purchaseService.createPurchase(request, idempotencyKey,
                new Actor(current.getId(), current.getUsername(), current.getBranchId()));
    }

    @GetMapping
    @Operation(summary = "Lister / filtrer les débits carte")
    public PageResponse<PurchaseSummaryResponse> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long cardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : FAR_FUTURE;
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<PurchaseSummaryResponse> result = purchaseService.search(customerId, cardId, fromInstant, toInstant,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un débit carte (lignes, reçu)")
    public PurchaseResponse get(@PathVariable Long id) {
        return purchaseService.get(id);
    }

    @GetMapping("/reference/{reference}")
    public PurchaseResponse getByReference(@PathVariable String reference) {
        return purchaseService.getByReference(reference);
    }
}
