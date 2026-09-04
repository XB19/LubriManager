package com.lubrimanager.reversal;

import com.lubrimanager.account.Account;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.branch.BranchRepository;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.common.money.MoneyRules;
import com.lubrimanager.common.reference.ReferenceCounterService;
import com.lubrimanager.inventory.InventoryService;
import com.lubrimanager.product.Product;
import com.lubrimanager.product.ProductRepository;
import com.lubrimanager.reversal.dto.ReversalResponse;
import com.lubrimanager.sale.Sale;
import com.lubrimanager.sale.SaleItem;
import com.lubrimanager.sale.SaleItemRepository;
import com.lubrimanager.sale.SaleRepository;
import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import com.lubrimanager.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Annulation / contre-passation d'une transaction validée (§14, §26).
 *
 * <p>La transaction d'origine <strong>n'est jamais supprimée</strong> : elle passe
 * au statut {@code ANNULEE} et une nouvelle écriture {@code ANNULATION} de montant
 * inverse lui est liée. Un motif est obligatoire. Annulation <strong>totale</strong>
 * uniquement (DEC-06). Pour une vente, le stock est restitué (mouvement ENTREE, RG-12).
 */
@Service
public class TransactionReversalService {

    private static final Logger log = LoggerFactory.getLogger(TransactionReversalService.class);
    private static final Set<TransactionType> REVERSIBLE =
            EnumSet.of(TransactionType.RECHARGE, TransactionType.ACHAT, TransactionType.AJUSTEMENT);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final ReferenceCounterService references;
    private final UserRepository users;
    private final BranchRepository branches;
    private final AuditService audit;
    private final Clock clock;

    public TransactionReversalService(TransactionRepository transactions, AccountRepository accounts,
                                      SaleRepository sales, SaleItemRepository saleItems, ProductRepository products,
                                      InventoryService inventory, ReferenceCounterService references,
                                      UserRepository users, BranchRepository branches, AuditService audit, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.sales = sales;
        this.saleItems = saleItems;
        this.products = products;
        this.inventory = inventory;
        this.references = references;
        this.users = users;
        this.branches = branches;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ReversalResponse reverse(Long originalTransactionId, String reason, Actor approver) {
        if (!StringUtils.hasText(reason)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Un motif d'annulation est obligatoire.");
        }
        Transaction original = transactions.findById(originalTransactionId)
                .orElseThrow(() -> ApiException.notFound("Transaction %d introuvable".formatted(originalTransactionId)));

        if (original.getStatus() != TransactionStatus.VALIDEE) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Seule une transaction validée peut être annulée (statut actuel : %s).".formatted(original.getStatus()));
        }
        if (!REVERSIBLE.contains(original.getType())) {
            throw ApiException.businessRule(
                    "Une transaction de type %s ne peut pas être annulée.".formatted(original.getType()));
        }

        Account account = accounts.lockById(original.getAccount().getId())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));

        BigDecimal reversalAmount = original.getAmount().negate();
        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(reversalAmount);
        if (after.signum() < 0) {
            throw ApiException.businessRule(
                    "Annulation impossible : le solde deviendrait négatif (les fonds ont déjà été utilisés). "
                            + "Solde %s FCFA, ajustement %s FCFA.".formatted(
                            before.toPlainString(), reversalAmount.toPlainString()));
        }

        String reference = references.nextDated("ANNL", LocalDate.now(clock), 6);
        Transaction reversal = new Transaction();
        reversal.setReference(reference);
        reversal.setType(TransactionType.ANNULATION);
        reversal.setStatus(TransactionStatus.VALIDEE);
        reversal.setAmount(MoneyRules.scaled(reversalAmount));
        reversal.setAccount(account);
        reversal.setCard(original.getCard());
        reversal.setCustomer(original.getCustomer());
        reversal.setUser(users.getReferenceById(approver.id()));
        reversal.setBranch(branches.getReferenceById(approver.branchId()));
        reversal.setBalanceBefore(MoneyRules.scaled(before));
        reversal.setBalanceAfter(MoneyRules.scaled(after));
        reversal.setRelatedTransaction(original);
        reversal.setDescription("Annulation de %s : %s".formatted(original.getReference(), reason.trim()));
        transactions.save(reversal);

        account.setBalance(after);
        original.setStatus(TransactionStatus.ANNULEE);

        if (original.getType() == TransactionType.ACHAT) {
            restock(original, reference, approver);
        }

        audit.recordChange(AuditAction.TRANSACTION_REVERSED, "transaction", original.getId(), original.getReference(),
                "Annulation de %s (%s FCFA) : %s".formatted(original.getReference(),
                        reversalAmount.toPlainString(), reason.trim()),
                Map.of("status", TransactionStatus.VALIDEE.name(), "balance", before.toPlainString()),
                Map.of("status", TransactionStatus.ANNULEE.name(), "balance", after.toPlainString()),
                approver);

        log.info("Annulation {} de {} par {} : solde {} → {}", reference, original.getReference(),
                approver.username(), before.toPlainString(), after.toPlainString());
        return ReversalResponse.of(reversal, original, approver.username());
    }

    private void restock(Transaction saleTransaction, String reversalReference, Actor approver) {
        Sale sale = sales.findByTransactionId(saleTransaction.getId()).orElse(null);
        if (sale == null) {
            return;
        }
        Map<Long, BigDecimal> quantityByProduct = new LinkedHashMap<>();
        for (SaleItem item : saleItems.findBySaleIdOrderByIdAsc(sale.getId())) {
            quantityByProduct.merge(item.getProduct().getId(), item.getQuantity(), BigDecimal::add);
        }
        for (Long productId : quantityByProduct.keySet().stream().sorted().toList()) {
            Product product = products.lockById(productId)
                    .orElseThrow(() -> ApiException.notFound("Produit %d introuvable".formatted(productId)));
            inventory.recordReversalInflow(product, quantityByProduct.get(productId), reversalReference, approver);
        }
    }
}
