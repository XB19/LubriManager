package com.lubrimanager.inventory;

/**
 * Nature d'un mouvement de stock (§20).
 *
 * <ul>
 *   <li>{@link #ENTREE} — réception ({@code quantity} &gt; 0).</li>
 *   <li>{@link #SORTIE} — sortie, exclusivement automatique lors d'une vente (Phase 8).</li>
 *   <li>{@link #AJUSTEMENT} — correction manuelle, {@code quantity} signé, motif obligatoire.</li>
 *   <li>{@link #INVENTAIRE} — recomptage : {@code quantity} = nouvelle quantité comptée (absolue), motif obligatoire.</li>
 * </ul>
 */
public enum InventoryMovementType {
    ENTREE,
    SORTIE,
    AJUSTEMENT,
    INVENTAIRE
}
