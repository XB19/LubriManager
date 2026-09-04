package com.lubrimanager.transaction;

import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation du grand livre (§18, §31). Aucun endpoint de suppression — une
 * transaction validée ne disparaît jamais (RG-10).
 */
@Tag(name = "Transactions")
@RestController
@RequestMapping("/api/transactions")
@PreAuthorize("isAuthenticated()")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "Lister / filtrer les transactions")
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) TransactionType type,
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

        Page<TransactionResponse> result = transactionService.search(type, status, accountId, customerId, cardId,
                fromInstant, toInstant,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une transaction")
    public TransactionResponse get(@PathVariable Long id) {
        return transactionService.get(id);
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Détail d'une transaction par sa référence")
    public TransactionResponse getByReference(@PathVariable String reference) {
        return transactionService.getByReference(reference);
    }
}
