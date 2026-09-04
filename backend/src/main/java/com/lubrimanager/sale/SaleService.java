package com.lubrimanager.sale;

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
import com.lubrimanager.inventory.InventoryService;
import com.lubrimanager.product.Product;
import com.lubrimanager.product.ProductRepository;
import com.lubrimanager.sale.dto.CreateSaleRequest;
import com.lubrimanager.sale.dto.SaleItemResponse;
import com.lubrimanager.sale.dto.SaleLineRequest;
import com.lubrimanager.sale.dto.SaleResponse;
import com.lubrimanager.sale.dto.SaleSummaryResponse;
import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import com.lubrimanager.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Ventes (§16-18). Tout se joue dans une seule transaction atomique :
 *
 * <ol>
 *   <li>verrou pessimiste sur le compte, puis sur chaque produit <strong>trié par id</strong>
 *       (ordre déterministe ⇒ pas d'inter-blocage) ;</li>
 *   <li>contrôles : carte ACTIVE, client ACTIF, produit vendable, stock suffisant,
 *       <strong>solde suffisant</strong> (RG-05) — sinon refus, rien n'est écrit ;</li>
 *   <li>débit du compte + écriture ACHAT au grand livre + vente + lignes (prix figés, RG-07)
 *       + une SORTIE de stock par ligne ;</li>
 *   <li>idempotence via l'en-tête {@code Idempotency-Key} (RG-09).</li>
 * </ol>
 */
@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);
    private static final String ENDPOINT = "POST /api/sales";
    private static final String REF_PREFIX = "VENT";
    private static final int REF_WIDTH = 6;

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final ReferenceCounterService references;
    private final IdempotencyService idempotency;
    private final UserRepository users;
    private final BranchRepository branches;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public SaleService(SaleRepository sales, SaleItemRepository saleItems, TransactionRepository transactions,
                       AccountRepository accounts, CardRepository cards, ProductRepository products,
                       InventoryService inventory, ReferenceCounterService references, IdempotencyService idempotency,
                       UserRepository users, BranchRepository branches, AuditService audit, Clock clock,
                       PlatformTransactionManager transactionManager) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.transactions = transactions;
        this.accounts = accounts;
        this.cards = cards;
        this.products = products;
        this.inventory = inventory;
        this.references = references;
        this.idempotency = idempotency;
        this.users = users;
        this.branches = branches;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------

    public SaleResponse createSale(CreateSaleRequest request, String idempotencyKey, Actor actor) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "En-tête « Idempotency-Key » requis pour cette opération.");
        }
        String hash = idempotency.hash(ENDPOINT, request);

        Optional<IdempotencyService.StoredResponse> replay = idempotency.lookup(idempotencyKey, hash);
        if (replay.isPresent()) {
            return idempotency.deserialize(replay.get(), SaleResponse.class);
        }
        try {
            return tx.execute(status -> doCreate(request, idempotencyKey, hash, actor));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return idempotency.lookup(idempotencyKey, hash)
                    .map(stored -> idempotency.deserialize(stored, SaleResponse.class))
                    .orElseThrow(() -> concurrentDuplicate);
        } catch (ApiException ex) {
            if (ex.code() == ErrorCode.BUSINESS_RULE) {
                // DEC-05 : la vente refusée ne laisse pas d'écriture au grand livre, seulement une trace d'audit.
                audit.record(AuditAction.SALE_REFUSED, "card", request.cardId(), null,
                        "Vente refusée : " + ex.getMessage(), actor);
            }
            throw ex;
        }
    }

    private SaleResponse doCreate(CreateSaleRequest req, String idempotencyKey, String hash, Actor actor) {
        idempotency.reserve(idempotencyKey, ENDPOINT, hash);

        Card card = cards.findById(req.cardId())
                .orElseThrow(() -> ApiException.notFound("Carte %d introuvable".formatted(req.cardId())));
        Account account = accounts.lockById(card.getAccount().getId())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        Customer customer = card.getCustomer();

        require(card.getStatus() == CardStatus.ACTIVE, "La carte n'est pas active.");
        require(customer.getStatus() == CustomerStatus.ACTIF, "Le client n'est pas actif.");
        require(account.isActive(), "Le compte n'est pas actif.");

        // Fusion des lignes par produit + contrôle des quantités.
        Map<Long, BigDecimal> quantityByProduct = new LinkedHashMap<>();
        for (SaleLineRequest line : req.items()) {
            require(MoneyRules.isStrictlyPositive(line.quantity()), "La quantité doit être positive.");
            require(MoneyRules.isWhole(line.quantity()), "La quantité doit être un nombre entier.");
            quantityByProduct.merge(line.productId(), line.quantity(), BigDecimal::add);
        }

        // Verrou des produits dans l'ordre croissant des id (anti-inter-blocage).
        List<Product> lockedProducts = new ArrayList<>();
        for (Long productId : quantityByProduct.keySet().stream().sorted().toList()) {
            Product product = products.lockById(productId)
                    .orElseThrow(() -> ApiException.notFound("Produit %d introuvable".formatted(productId)));
            require(product.isSellable(), "Le produit « %s » n'est pas disponible à la vente.".formatted(product.getName()));
            BigDecimal wanted = quantityByProduct.get(productId);
            require(product.getStockQuantity().compareTo(wanted) >= 0,
                    "Stock insuffisant pour « %s » (%s en stock, %s demandé).".formatted(
                            product.getName(), product.getStockQuantity().toPlainString(), wanted.toPlainString()));
            lockedProducts.add(product);
        }

        // Calcul des lignes (prix figés) et des totaux.
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        List<SaleItem> items = new ArrayList<>();
        for (Product product : lockedProducts) {
            BigDecimal quantity = quantityByProduct.get(product.getId());
            BigDecimal unitPrice = product.getUnitPrice();
            BigDecimal lineTotal = SaleCalculation.lineTotal(unitPrice, quantity);
            subtotal = subtotal.add(lineTotal);
            tax = tax.add(MoneyRules.taxOf(lineTotal, product.getVatRate()));

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setProductReference(product.getReference());
            item.setQuantity(quantity);
            item.setUnitPrice(MoneyRules.scaled(unitPrice));
            item.setVatRate(product.getVatRate());
            item.setLineTotal(MoneyRules.scaled(lineTotal));
            items.add(item);
        }
        BigDecimal total = subtotal; // prix TTC (DEC-04)

        // RG-05 : solde suffisant. Sinon refus — rien n'est écrit (rollback).
        BigDecimal before = account.getBalance();
        if (before.compareTo(total) < 0) {
            log.info("Vente refusée (solde insuffisant) carte {} : {} < {}",
                    card.getCardNo(), before.toPlainString(), total.toPlainString());
            // TODO Phase 9 : consigner ACHAT_REFUSE dans l'AuditLog (transaction dédiée).
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
        txn.setDescription("Vente %s • %d ligne(s) • %s FCFA%s".formatted(reference, items.size(),
                total.toPlainString(), StringUtils.hasText(req.comment()) ? " • " + req.comment().trim() : ""));
        transactions.save(txn);

        Sale sale = new Sale();
        sale.setTransaction(txn);
        sale.setSubtotal(MoneyRules.scaled(subtotal));
        sale.setTax(tax.setScale(2, RoundingMode.HALF_UP));
        sale.setTotal(MoneyRules.scaled(total));
        sales.save(sale);

        for (SaleItem item : items) {
            item.setSale(sale);
        }
        saleItems.saveAll(items);

        for (Product product : lockedProducts) {
            inventory.recordSaleOutflow(product, quantityByProduct.get(product.getId()), reference, actor);
        }

        SaleResponse response = build(sale, txn, account, card, customer, items, actor.username());
        idempotency.complete(idempotencyKey, 201, response);
        audit.record(AuditAction.SALE_COMPLETED, "sale", sale.getId(), reference,
                "Vente %s • %d ligne(s) • %s FCFA sur %s (%s) — solde %s → %s".formatted(reference, items.size(),
                        total.toPlainString(), card.getCardNo(), customer.getCustomerNo(),
                        before.toPlainString(), after.toPlainString()),
                actor);
        log.info("Vente {} : {} FCFA sur {} ({}) — solde {} → {}", reference, total.toPlainString(),
                card.getCardNo(), customer.getCustomerNo(), before.toPlainString(), after.toPlainString());
        return response;
    }

    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<SaleSummaryResponse> search(Long customerId, Long cardId, Instant from, Instant to, Pageable pageable) {
        return sales.search(customerId, cardId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        Sale sale = sales.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Vente %d introuvable".formatted(id)));
        return toResponse(sale);
    }

    @Transactional(readOnly = true)
    public SaleResponse getByReference(String reference) {
        Sale sale = sales.findDetailByReference(reference)
                .orElseThrow(() -> ApiException.notFound("Vente %s introuvable".formatted(reference)));
        return toResponse(sale);
    }

    // ------------------------------------------------------------------

    private void require(boolean condition, String message) {
        if (!condition) {
            throw ApiException.businessRule(message);
        }
    }

    private SaleResponse toResponse(Sale sale) {
        Transaction txn = sale.getTransaction();
        List<SaleItem> items = saleItems.findBySaleIdOrderByIdAsc(sale.getId());
        return build(sale, txn, txn.getAccount(), txn.getCard(), txn.getCustomer(), items,
                txn.getUser().getUsername());
    }

    private SaleResponse build(Sale sale, Transaction txn, Account account, Card card, Customer customer,
                               List<SaleItem> items, String operator) {
        return new SaleResponse(
                sale.getId(),
                txn.getReference(),
                txn.getId(),
                txn.getStatus(),
                account.getId(),
                customer.getId(),
                customer.getCustomerNo(),
                customer.getFullName(),
                card != null ? card.getCardNo() : null,
                sale.getSubtotal(),
                sale.getTax(),
                sale.getTotal(),
                txn.getBalanceBefore(),
                txn.getBalanceAfter(),
                operator,
                txn.getDescription(),
                sale.getCreatedAt(),
                items.stream().map(SaleItemResponse::from).toList());
    }
}
