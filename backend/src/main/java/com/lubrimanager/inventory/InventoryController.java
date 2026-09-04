package com.lubrimanager.inventory;

import com.lubrimanager.common.Actor;
import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.inventory.dto.InventoryMovementResponse;
import com.lubrimanager.inventory.dto.RecordMovementRequest;
import com.lubrimanager.product.Product;
import com.lubrimanager.product.ProductService;
import com.lubrimanager.product.ProductStatus;
import com.lubrimanager.product.dto.ProductResponse;
import com.lubrimanager.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stock")
@RestController
@PreAuthorize("isAuthenticated()")
public class InventoryController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final InventoryService inventoryService;
    private final ProductService productService;

    public InventoryController(InventoryService inventoryService, ProductService productService) {
        this.inventoryService = inventoryService;
        this.productService = productService;
    }

    @PostMapping("/api/inventory/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Enregistrer une entrée, un ajustement ou un inventaire")
    public InventoryMovementResponse record(@Valid @RequestBody RecordMovementRequest request,
                                            @AuthenticationPrincipal AppUserDetails current) {
        return inventoryService.record(request,
                new Actor(current.getId(), current.getUsername(), current.getBranchId()));
    }

    @GetMapping("/api/inventory/movements")
    @Operation(summary = "Lister / filtrer les mouvements de stock")
    public PageResponse<InventoryMovementResponse> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : FAR_FUTURE;
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<InventoryMovementResponse> result = inventoryService.search(productId, type, fromInstant, toInstant,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @GetMapping("/api/inventory/movements/{id}")
    @Operation(summary = "Détail d'un mouvement de stock")
    public InventoryMovementResponse get(@PathVariable Long id) {
        return inventoryService.get(id);
    }

    @GetMapping("/api/inventory/low-stock")
    @Operation(summary = "Produits actifs dont le stock est au niveau d'alerte ou en dessous")
    public PageResponse<ProductResponse> lowStock(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Product> result = productService.search(null, null, ProductStatus.ACTIF, true,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("name").ascending()));
        return PageResponse.of(result, ProductResponse::from);
    }

    @GetMapping("/api/products/{productId}/movements")
    @Operation(summary = "Historique de stock d'un produit")
    public List<InventoryMovementResponse> byProduct(@PathVariable Long productId,
                                                     @RequestParam(defaultValue = "50") int limit) {
        return inventoryService.listByProduct(productId, limit);
    }
}
