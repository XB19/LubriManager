package com.lubrimanager.inventory;

import java.math.BigDecimal;

/**
 * Calcul pur d'un mouvement de stock : à partir du type, du stock courant et de
 * la valeur saisie, renvoie le delta signé effectivement appliqué et le stock
 * résultant. Aucune validation ici (le service lève les erreurs métier).
 */
public final class InventoryCalculation {

    private InventoryCalculation() {
    }

    public record Applied(BigDecimal delta, BigDecimal stockAfter) {
    }

    /**
     * @param input pour ENTREE/SORTIE : quantité (positive) ; pour AJUSTEMENT : delta signé ;
     *              pour INVENTAIRE : nouvelle quantité comptée (absolue).
     */
    public static Applied apply(InventoryMovementType type, BigDecimal stockBefore, BigDecimal input) {
        return switch (type) {
            case ENTREE -> new Applied(input, stockBefore.add(input));
            case SORTIE -> new Applied(input.negate(), stockBefore.subtract(input));
            case AJUSTEMENT -> new Applied(input, stockBefore.add(input));
            case INVENTAIRE -> new Applied(input.subtract(stockBefore), input);
        };
    }
}
