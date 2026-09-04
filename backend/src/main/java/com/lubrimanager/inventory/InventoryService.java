package com.lubrimanager.inventory;

import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.branch.BranchRepository;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.notification.NotificationService;
import com.lubrimanager.notification.NotificationSeverity;
import com.lubrimanager.notification.NotificationType;
import com.lubrimanager.inventory.dto.InventoryMovementResponse;
import com.lubrimanager.inventory.dto.RecordMovementRequest;
import com.lubrimanager.product.Product;
import com.lubrimanager.product.ProductRepository;
import com.lubrimanager.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Mouvements de stock. Chaque mouvement est atomique : verrou pessimiste sur le
 * produit, écriture du mouvement (ajout seul) et mise à jour du cache
 * {@code product.stockQuantity} dans la même transaction. Le stock ne peut jamais
 * devenir négatif.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryMovementRepository movements;
    private final ProductRepository products;
    private final UserRepository users;
    private final BranchRepository branches;
    private final AuditService audit;
    private final NotificationService notifications;

    public InventoryService(InventoryMovementRepository movements, ProductRepository products,
                            UserRepository users, BranchRepository branches, AuditService audit,
                            NotificationService notifications) {
        this.movements = movements;
        this.products = products;
        this.users = users;
        this.branches = branches;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public InventoryMovementResponse record(RecordMovementRequest req, Actor actor) {
        if (req.type() == InventoryMovementType.SORTIE) {
            throw ApiException.businessRule("La sortie de stock est enregistrée automatiquement lors d'une vente.");
        }

        BigDecimal input = req.quantity();
        switch (req.type()) {
            case ENTREE -> require(input.signum() > 0, "La quantité d'entrée doit être positive.");
            case AJUSTEMENT -> {
                require(input.signum() != 0, "L'ajustement ne peut pas être nul.");
                require(StringUtils.hasText(req.reason()), "Un motif est obligatoire pour un ajustement.");
            }
            case INVENTAIRE -> {
                require(input.signum() >= 0, "La quantité comptée ne peut pas être négative.");
                require(StringUtils.hasText(req.reason()), "Un motif est obligatoire pour un inventaire.");
            }
            default -> { /* SORTIE déjà écartée */ }
        }

        Product product = products.lockById(req.productId())
                .orElseThrow(() -> ApiException.notFound("Produit %d introuvable".formatted(req.productId())));

        BigDecimal before = product.getStockQuantity();
        var applied = InventoryCalculation.apply(req.type(), before, input);
        require(applied.stockAfter().signum() >= 0, "Ce mouvement rendrait le stock négatif.");

        product.setStockQuantity(applied.stockAfter());

        InventoryMovement movement = persist(product, req.type(), applied.delta(), before, applied.stockAfter(),
                clean(req.reference()), clean(req.reason()), actor);

        audit.recordChange(AuditAction.INVENTORY_MOVEMENT, "product", product.getId(), product.getReference(),
                "Stock %s : %s %s → %s".formatted(product.getReference(), req.type(),
                        before.toPlainString(), applied.stockAfter().toPlainString()),
                java.util.Map.of("stock", before.toPlainString()),
                java.util.Map.of("stock", applied.stockAfter().toPlainString()),
                actor);

        log.info("Stock {} : {} {} → {} ({}{})", product.getReference(), before.toPlainString(),
                req.type(), applied.stockAfter().toPlainString(),
                applied.delta().signum() >= 0 ? "+" : "", applied.delta().toPlainString());
        alertIfCrossedLowThreshold(product, before, applied.stockAfter());
        return InventoryMovementResponse.from(movement);
    }

    /** Notifie le staff seulement quand le stock <em>vient de</em> passer au niveau d'alerte (RG-14). */
    private void alertIfCrossedLowThreshold(Product product, BigDecimal before, BigDecimal after) {
        boolean wasLow = before.compareTo(product.getAlertThreshold()) <= 0;
        boolean isLow = after.compareTo(product.getAlertThreshold()) <= 0;
        if (isLow && !wasLow) {
            notifications.staffAlert(NotificationType.STOCK_LOW, NotificationSeverity.ALERTE,
                    "Stock bas : " + product.getName(),
                    "%s (%s) : %s en stock, seuil %s.".formatted(product.getName(), product.getReference(),
                            after.toPlainString(), product.getAlertThreshold().toPlainString()),
                    product.getReference());
        }
    }

    /**
     * Sortie de stock consécutive à une vente (Phase 8). À appeler dans la transaction
     * de la vente, avec un produit <strong>déjà verrouillé</strong>.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovement recordSaleOutflow(Product lockedProduct, BigDecimal quantity, String saleReference,
                                               Actor actor) {
        BigDecimal before = lockedProduct.getStockQuantity();
        var applied = InventoryCalculation.apply(InventoryMovementType.SORTIE, before, quantity);
        if (applied.stockAfter().signum() < 0) {
            throw ApiException.businessRule(
                    "Stock insuffisant pour « %s » (%s en stock, %s demandé)."
                            .formatted(lockedProduct.getName(), before.toPlainString(), quantity.toPlainString()));
        }
        lockedProduct.setStockQuantity(applied.stockAfter());
        InventoryMovement movement = persist(lockedProduct, InventoryMovementType.SORTIE, applied.delta(),
                before, applied.stockAfter(), saleReference, null, actor);
        alertIfCrossedLowThreshold(lockedProduct, before, applied.stockAfter());
        return movement;
    }

    /**
     * Retour de stock consécutif à l'annulation d'une vente (Phase 9), avec produit
     * <strong>déjà verrouillé</strong>. Mouvement ENTREE tracé (RG-12).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovement recordReversalInflow(Product lockedProduct, BigDecimal quantity, String saleReference,
                                                  Actor actor) {
        BigDecimal before = lockedProduct.getStockQuantity();
        var applied = InventoryCalculation.apply(InventoryMovementType.ENTREE, before, quantity);
        lockedProduct.setStockQuantity(applied.stockAfter());
        return persist(lockedProduct, InventoryMovementType.ENTREE, applied.delta(), before, applied.stockAfter(),
                saleReference, "Annulation de " + saleReference, actor);
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementResponse> search(Long productId, InventoryMovementType type,
                                                  Instant from, Instant to, Pageable pageable) {
        return movements.search(productId, type, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public InventoryMovementResponse get(Long id) {
        return movements.findDetailById(id)
                .map(InventoryMovementResponse::from)
                .orElseThrow(() -> ApiException.notFound("Mouvement de stock %d introuvable".formatted(id)));
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> listByProduct(Long productId, int limit) {
        if (!products.existsById(productId)) {
            throw ApiException.notFound("Produit %d introuvable".formatted(productId));
        }
        return movements.findByProduct(productId, PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));
    }

    // ------------------------------------------------------------------

    private InventoryMovement persist(Product product, InventoryMovementType type, BigDecimal delta,
                                      BigDecimal before, BigDecimal after, String reference, String reason,
                                      Actor actor) {
        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(delta);
        movement.setStockBefore(before);
        movement.setStockAfter(after);
        movement.setReference(reference);
        movement.setReason(reason);
        movement.setUser(users.getReferenceById(actor.id()));
        movement.setBranch(branches.getReferenceById(actor.branchId()));
        return movements.save(movement);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw ApiException.businessRule(message);
        }
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
