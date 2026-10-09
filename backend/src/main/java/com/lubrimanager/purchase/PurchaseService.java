package com.lubrimanager.purchase;

import com.lubrimanager.account.Account;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.branch.BranchRepository;
import com.lubrimanager.card.Card;
import com.lubrimanager.card.CardRepository;
import com.lubrimanager.card.CardStatus;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.common.idempotency.IdempotencyService;
import com.lubrimanager.common.money.MoneyRules;
import com.lubrimanager.common.reference.ReferenceCounterService;
import com.lubrimanager.customer.Customer;
import com.lubrimanager.customer.CustomerStatus;
import com.lubrimanager.product.Product;
import com.lubrimanager.product.ProductRepository;
import com.lubrimanager.purchase.dto.CreatePurchaseRequest;
import com.lubrimanager.purchase.dto.PurchaseItemResponse;
import com.lubrimanager.purchase.dto.PurchaseLineRequest;
import com.lubrimanager.purchase.dto.PurchaseResponse;
import com.lubrimanager.purchase.dto.PurchaseSummaryResponse;
import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import com.lubrimanager.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Débit carte : achat d'un ou plusieurs produits réglé sur le solde du compte.
 * Tout se joue dans une seule transaction atomique :
 *
 * <ol>
 *   <li>verrou pessimiste sur le compte, puis sur chaque produit <strong>trié par id</strong>
 *       (ordre déterministe ⇒ pas d'inter-blocage) ;</li>
 *   <li>contrôles : carte ACTIVE, client ACTIF, compte actif, <strong>solde suffisant</strong>
 *       (RG-05) — sinon refus, rien n'est écrit ;</li>
 *   <li>débit du compte + écriture ACHAT au grand livre + débit + lignes (prix figés) ;</li>
 *   <li>idempotence via l'en-tête {@code Idempotency-Key}.</li>
 * </ol>
 *
 * <p>Pas de gestion de stock : un produit désactivé n'existe plus (le catalogue est minimal).
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);
    private static final String ENDPOINT = "POST /api/purchases";
    private static final String REF_PREFIX = "ACHT";
    private static final int REF_WIDTH = 6;

    private final PurchaseRepository purchases;
    private final PurchaseItemRepository purchaseItems;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final ProductRepository products;
    private final ReferenceCounterService references;
    private final IdempotencyService idempotency;
    private final UserRepository users;
    private final BranchRepository branches;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PurchaseService(PurchaseRepository purchases, PurchaseItemRepository purchaseItems,
                           TransactionRepository transactions, AccountRepository accounts, CardRepository cards,
                           ProductRepository products, ReferenceCounterService references,
                           IdempotencyService idempotency, UserRepository users, BranchRepository branches,
                           AuditService audit, Clock clock, PlatformTransactionManager transactionManager) {
        this.purchases = purchases;
        this.purchaseItems = purchaseItems;
        this.transactions = transactions;
        this.accounts = accounts;
        this.cards = cards;
        this.products = products;
        this.references = references;
        this.idempotency = idempotency;
        this.users = users;
        this.branches = branches;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------

    public PurchaseResponse createPurchase(CreatePurchaseRequest request, String idempotencyKey, Actor actor) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "En-tête « Idempotency-Key » requis pour cette opération.");
        }
        String hash = idempotency.hash(ENDPOINT, request);

        Optional<IdempotencyService.StoredResponse> replay = idempotency.lookup(idempotencyKey, hash);
        if (replay.isPresent()) {
            return idempotency.deserialize(replay.get(), PurchaseResponse.class);
        }
        try {
            return tx.execute(status -> doCreate(request, idempotencyKey, hash, actor));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return idempotency.lookup(idempotencyKey, hash)
                    .map(stored -> idempotency.deserialize(stored, PurchaseResponse.class))
                    .orElseThrow(() -> concurrentDuplicate);
        } catch (ApiException ex) {
            if (ex.code() == ErrorCode.BUSINESS_RULE) {
                audit.record(AuditAction.PURCHASE_REFUSED, "card", request.cardId(), null,
                        "Débit carte refusé : " + ex.getMessage(), actor);
            }
            throw ex;
        }
    }

    private PurchaseResponse doCreate(CreatePurchaseRequest req, String idempotencyKey, String hash, Actor actor) {
        idempotency.reserve(idempotencyKey, ENDPOINT, hash);

        Card card = cards.findById(req.cardId())
                .orElseThrow(() -> ApiException.notFound("Carte %d introuvable".formatted(req.cardId())));
        Account account = accounts.lockById(card.getAccount().getId())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        Customer customer = card.getCustomer();

        require(card.getStatus() == CardStatus.ACTIVE, "La carte n'est pas active.");
        require(customer.getStatus() == CustomerStatus.ACTIF, "Le client n'est pas actif.");
        require(account.isActive(), "Le compte n'est pas actif.");

        // Fusion des lignes par produit + contrôle des quantités (entières).
        Map<Long, BigDecimal> quantityByProduct = new LinkedHashMap<>();
        for (PurchaseLineRequest line : req.items()) {
            require(MoneyRules.isStrictlyPositive(line.quantity()), "La quantité doit être positive.");
            require(MoneyRules.isWhole(line.quantity()), "La quantité doit être un nombre entier.");
            quantityByProduct.merge(line.productId(), line.quantity(), BigDecimal::add);
        }

        // Verrou des produits dans l'ordre croissant des id (anti-inter-blocage).
        List<Product> lockedProducts = new ArrayList<>();
        for (Long productId : quantityByProduct.keySet().stream().sorted().toList()) {
            Product product = products.lockById(productId)
                    .orElseThrow(() -> ApiException.notFound("Produit %d introuvable".formatted(productId)));
            lockedProducts.add(product);
        }

        // Calcul des lignes (prix figés) et du total.
        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseItem> items = new ArrayList<>();
        for (Product product : lockedProducts) {
            BigDecimal quantity = quantityByProduct.get(product.getId());
            BigDecimal unitPrice = product.getUnitPrice();
            BigDecimal lineTotal = PurchaseCalculation.lineTotal(unitPrice, quantity);
            total = total.add(lineTotal);

            PurchaseItem item = new PurchaseItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setProductReference(product.getReference());
            item.setQuantity(quantity);
            item.setUnitPrice(MoneyRules.scaled(unitPrice));
            item.setLineTotal(MoneyRules.scaled(lineTotal));
            items.add(item);
        }

        // RG-05 : solde suffisant. Sinon refus — rien n'est écrit (rollback).
        BigDecimal before = account.getBalance();
        if (before.compareTo(total) < 0) {
            log.info("Débit carte refusé (solde insuffisant) carte {} : {} < {}",
                    card.getCardNo(), before.toPlainString(), total.toPlainString());
            throw new ApiException(ErrorCode.BUSINESS_RULE,
                    "Solde insuffisant : %s FCFA disponibles, %s FCFA requis.".formatted(
                            before.toPlainString(), total.toPlainString()));
        }
        BigDecimal after = before.subtract(total);
        account.setBalance(after);

        String reference = references.nextDated(REF_PREFIX, LocalDate.now(clock), REF_WIDTH);

        Transaction txn = new Transaction();
        txn.setReference(reference);
        txn.setType(TransactionType.ACHAT);
        txn.setStatus(TransactionStatus.VALIDEE);
        txn.setAmount(MoneyRules.scaled(total.negate()));
        txn.setAccount(account);
        txn.setCard(card);
        txn.setCustomer(customer);
        txn.setUser(users.getReferenceById(actor.id()));
        txn.setBranch(branches.getReferenceById(actor.branchId()));
        txn.setBalanceBefore(MoneyRules.scaled(before));
        txn.setBalanceAfter(MoneyRules.scaled(after));
        txn.setIdempotencyKey(idempotencyKey);
        txn.setDescription("Débit carte %s • %d ligne(s) • %s FCFA%s".formatted(reference, items.size(),
                total.toPlainString(), StringUtils.hasText(req.comment()) ? " • " + req.comment().trim() : ""));
        transactions.save(txn);

        Purchase purchase = new Purchase();
        purchase.setTransaction(txn);
        purchase.setTotal(MoneyRules.scaled(total));
        purchases.save(purchase);

        for (PurchaseItem item : items) {
            item.setPurchase(purchase);
        }
        purchaseItems.saveAll(items);

        PurchaseResponse response = build(purchase, txn, account, card, customer, items, actor.username());
        idempotency.complete(idempotencyKey, 201, response);
        audit.record(AuditAction.PURCHASE_COMPLETED, "card_purchase", purchase.getId(), reference,
                "Débit carte %s • %d ligne(s) • %s FCFA sur %s (%s) — solde %s → %s".formatted(reference, items.size(),
                        total.toPlainString(), card.getCardNo(), customer.getCustomerNo(),
                        before.toPlainString(), after.toPlainString()),
                actor);
        log.info("Débit carte {} : {} FCFA sur {} ({}) — solde {} → {}", reference, total.toPlainString(),
                card.getCardNo(), customer.getCustomerNo(), before.toPlainString(), after.toPlainString());
        return response;
    }

    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<PurchaseSummaryResponse> search(Long customerId, Long cardId, Instant from, Instant to,
                                                Pageable pageable) {
        return purchases.search(customerId, cardId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(Long id) {
        Purchase purchase = purchases.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Débit carte %d introuvable".formatted(id)));
        return toResponse(purchase);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getByReference(String reference) {
        Purchase purchase = purchases.findDetailByReference(reference)
                .orElseThrow(() -> ApiException.notFound("Débit carte %s introuvable".formatted(reference)));
        return toResponse(purchase);
    }

    // ------------------------------------------------------------------

    private void require(boolean condition, String message) {
        if (!condition) {
            throw ApiException.businessRule(message);
        }
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        Transaction txn = purchase.getTransaction();
        List<PurchaseItem> items = purchaseItems.findByPurchaseIdOrderByIdAsc(purchase.getId());
        return build(purchase, txn, txn.getAccount(), txn.getCard(), txn.getCustomer(), items,
                txn.getUser().getUsername());
    }

    private PurchaseResponse build(Purchase purchase, Transaction txn, Account account, Card card, Customer customer,
                                   List<PurchaseItem> items, String operator) {
        return new PurchaseResponse(
                purchase.getId(),
                txn.getReference(),
                txn.getId(),
                txn.getStatus(),
                account.getId(),
                customer.getId(),
                customer.getCustomerNo(),
                customer.getFullName(),
                card != null ? card.getCardNo() : null,
                purchase.getTotal(),
                txn.getBalanceBefore(),
                txn.getBalanceAfter(),
                operator,
                txn.getDescription(),
                purchase.getCreatedAt(),
                items.stream().map(PurchaseItemResponse::from).toList());
    }
}
