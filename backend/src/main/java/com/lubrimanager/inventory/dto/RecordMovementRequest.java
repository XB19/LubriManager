package com.lubrimanager.inventory.dto;

import com.lubrimanager.inventory.InventoryMovementType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Enregistrement d'un mouvement de stock manuel.
 *
 * <p>Selon {@code type} : ENTREE ⇒ {@code quantity} &gt; 0 ; AJUSTEMENT ⇒
 * {@code quantity} signé ≠ 0 + {@code reason} ; INVENTAIRE ⇒ {@code quantity} =
 * nouvelle quantité comptée ≥ 0 + {@code reason}. SORTIE n'est pas acceptée ici.
 */
public record RecordMovementRequest(
        @NotNull InventoryMovementType type,
        @NotNull Long productId,
        @NotNull @Digits(integer = 13, fraction = 2) BigDecimal quantity,
        @Size(max = 60) String reference,
        @Size(max = 500) String reason) {
}
