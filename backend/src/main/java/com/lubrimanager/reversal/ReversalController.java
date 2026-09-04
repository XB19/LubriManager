package com.lubrimanager.reversal;

import com.lubrimanager.common.Actor;
import com.lubrimanager.reversal.dto.ReversalResponse;
import com.lubrimanager.reversal.dto.ReverseTransactionRequest;
import com.lubrimanager.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions")
@RestController
public class ReversalController {

    private final TransactionReversalService reversalService;

    public ReversalController(TransactionReversalService reversalService) {
        this.reversalService = reversalService;
    }

    @PostMapping("/api/transactions/{id}/reversal")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Annuler (contre-passer) une transaction validée — motif obligatoire")
    public ResponseEntity<ReversalResponse> reverse(@PathVariable Long id,
                                                    @Valid @RequestBody ReverseTransactionRequest request,
                                                    @AuthenticationPrincipal AppUserDetails current) {
        Actor actor = new Actor(current.getId(), current.getUsername(), current.getBranchId());
        ReversalResponse response = reversalService.reverse(id, request.reason(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
