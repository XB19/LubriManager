package com.lubrimanager.card;

import com.lubrimanager.card.dto.CardResponse;
import com.lubrimanager.card.dto.CreateCardRequest;
import com.lubrimanager.card.dto.DeactivateCardRequest;
import com.lubrimanager.card.dto.UpdateCardStatusRequest;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * API cartes. Lecture pour tout utilisateur authentifié ; émission et statuts
 * opérationnels pour ADMIN / MANAGER / AGENT ; désactivation définitive réservée
 * à ADMIN / MANAGER (matrice RBAC §10).
 */
@Tag(name = "Cartes")
@RestController
@PreAuthorize("isAuthenticated()")
public class CardController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/api/cards")
    @Operation(summary = "Rechercher / lister les cartes")
    public PageResponse<CardResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) CardStatus status,
                                           @RequestParam(required = false) Long customerId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Card> result = cardService.search(q, status, customerId,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("cardNo").ascending()));
        return PageResponse.of(result, CardResponse::from);
    }

    @GetMapping("/api/cards/{id}")
    @Operation(summary = "Détail d'une carte")
    public CardResponse get(@PathVariable Long id) {
        return CardResponse.from(cardService.get(id));
    }

    @GetMapping("/api/customers/{customerId}/cards")
    @Operation(summary = "Cartes d'un client")
    public List<CardResponse> byCustomer(@PathVariable Long customerId) {
        return cardService.listByCustomer(customerId).stream().map(CardResponse::from).toList();
    }

    @PostMapping("/api/cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    @Operation(summary = "Émettre une carte (n° attribué automatiquement)")
    public ResponseEntity<CardResponse> create(@Valid @RequestBody CreateCardRequest request,
                                               UriComponentsBuilder uriBuilder) {
        Card created = cardService.create(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/cards/{id}").buildAndExpand(created.getId()).toUri())
                .body(CardResponse.from(created));
    }

    @PatchMapping("/api/cards/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    @Operation(summary = "Activer / suspendre / bloquer / déclarer perdue une carte")
    public CardResponse changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateCardStatusRequest request,
                                     @AuthenticationPrincipal AppUserDetails current) {
        return CardResponse.from(cardService.changeStatus(id, request.status(), request.reason(), actor(current)));
    }

    @PostMapping("/api/cards/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Désactiver définitivement une carte (remplacement = nouvelle carte)")
    public CardResponse deactivate(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) DeactivateCardRequest request,
                                   @AuthenticationPrincipal AppUserDetails current) {
        String reason = request != null ? request.reason() : null;
        return CardResponse.from(cardService.deactivate(id, reason, actor(current)));
    }

    private static Actor actor(AppUserDetails current) {
        return new Actor(current.getId(), current.getUsername(), current.getBranchId());
    }
}
