package com.lubrimanager.recharge;

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
import com.lubrimanager.notification.NotificationService;
import com.lubrimanager.notification.NotificationSeverity;
import com.lubrimanager.notification.NotificationType;
import com.lubrimanager.payment.PaymentMethod;
import com.lubrimanager.payment.PaymentMethodService;
import com.lubrimanager.recharge.dto.CreateRechargeRequest;
import com.lubrimanager.recharge.dto.RechargeResponse;
import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * Recharges (crédit d'un compte).
 *
 * <p>Intégrité financière (Règle n°2, §15, §23-24) :
 * <ul>
 *   <li><b>Atomicité</b> — l'écriture au grand livre et la mise à jour du solde
 *       sont dans une seule transaction ; toute erreur ⇒ ROLLBACK complet.</li>
 *   <li><b>Concurrence</b> — verrou pessimiste sur la ligne compte.</li>
 *   <li><b>Idempotence</b> — clé {@code Idempotency-Key} obligatoire ; contrainte
 *       d'unicité en base = garde anti-doublon (RG-09).</li>
 *   <li><b>Validation MANAGER</b> — au-delà d'un seuil (DEC-03), la recharge est
 *       créée EN_ATTENTE et le solde n'est mouvementé qu'à la validation.</li>
 * </ul>
 */
@Service
public class RechargeService {

    private static final Logger log = LoggerFactory.getLogger(RechargeService.class);
    private static final String ENDPOINT = "POST /api/recharges";
    private static final String REF_PREFIX = "RECH";
    private static final int REF_WIDTH = 6;

    private final RechargeRepository recharges;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final PaymentMethodService paymentMethods;
    private final ReferenceCounterService references;
    private final IdempotencyService idempotency;
    private final RechargeProperties properties;
    private final BranchRepository branches;
    private final com.lubrimanager.user.UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate tx;

    public RechargeService(RechargeRepository recharges, TransactionRepository transactions,
                           AccountRepository accounts, CardRepository cards, PaymentMethodService paymentMethods,
                           ReferenceCounterService references, IdempotencyService idempotency,
                           RechargeProperties properties, BranchRepository branches,
                           com.lubrimanager.user.UserRepository users, AuditService audit,
                           NotificationService notifications, Clock clock,
                           PlatformTransactionManager transactionManager) {
        this.recharges = recharges;
        this.transactions = transactions;
        this.accounts = accounts;
        this.cards = cards;
        this.paymentMethods = paymentMethods;
        this.references = references;
        this.idempotency = idempotency;
        this.properties = properties;
        this.branches = branches;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------
    // Création (avec idempotence)
    // ------------------------------------------------------------------

    public RechargeResponse createRecharge(CreateRechargeRequest request, String idempotencyKey, Actor actor) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "En-tête « Idempotency-Key » requis pour cette opération.");
        }
        String hash = idempotency.hash(ENDPOINT, request);

        Optional<IdempotencyService.StoredResponse> replay = idempotency.lookup(idempotencyKey, hash);
        if (replay.isPresent()) {
            return idempotency.deserialize(replay.get(), RechargeResponse.class);
        }
        try {
            return tx.execute(status -> doCreate(request, idempotencyKey, hash, actor));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Une requête concurrente portant la même clé a validé en premier.
            return idempotency.lookup(idempotencyKey, hash)
                    .map(stored -> idempotency.deserialize(stored, RechargeResponse.class))
                    .orElseThrow(() -> concurrentDuplicate);
        }
    }

    private RechargeResponse doCreate(CreateRechargeRequest req, String idempotencyKey, String hash, Actor actor) {
        idempotency.reserve(idempotencyKey, ENDPOINT, hash); // saveAndFlush ⇒ doublon rejeté ici

        Card card = cards.findById(req.cardId())
                .orElseThrow(() -> ApiException.notFound("Carte %d introuvable".formatted(req.cardId())));
        Account account = accounts.lockById(card.getAccount().getId())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        Customer customer = card.getCustomer();

        require(card.getStatus() == CardStatus.ACTIVE, "La carte n'est pas active.");
        require(customer.getStatus() == CustomerStatus.ACTIF, "Le client n'est pas actif.");
        require(account.isActive(), "Le compte n'est pas actif.");

        BigDecimal amount = req.amount();
        require(MoneyRules.isStrictlyPositive(amount), "Le montant doit être positif.");
        require(MoneyRules.isWhole(amount), "Le montant doit être un nombre entier de FCFA.");
        require(amount.compareTo(properties.maxAmount()) <= 0,
                "Montant supérieur au plafond autorisé (%s FCFA).".formatted(properties.maxAmount().toPlainString()));

        PaymentMethod method = paymentMethods.requireActive(req.paymentMethodId());

        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(amount);
        boolean pending = amount.compareTo(properties.approvalThreshold()) > 0;
        TransactionStatus status = pending ? TransactionStatus.EN_ATTENTE : TransactionStatus.VALIDEE;

        String reference = references.nextDated(REF_PREFIX, LocalDate.now(clock), REF_WIDTH);

        Transaction txn = new Transaction();
        txn.setReference(reference);
        txn.setType(TransactionType.RECHARGE);
        txn.setStatus(status);
        txn.setAmount(MoneyRules.scaled(amount));
        txn.setAccount(account);
        txn.setCard(card);
        txn.setCustomer(customer);
        txn.setUser(users.getReferenceById(actor.id()));
        txn.setBranch(branches.getReferenceById(actor.branchId()));
        txn.setBalanceBefore(MoneyRules.scaled(before));
        txn.setBalanceAfter(MoneyRules.scaled(after));
        txn.setIdempotencyKey(idempotencyKey);
        txn.setDescription("Recharge %s • %s%s".formatted(reference, method.getCode(),
                StringUtils.hasText(req.comment()) ? " • " + req.comment().trim() : ""));
        transactions.save(txn);

        if (!pending) {
            account.setBalance(after); // RG-00 : le solde ne bouge que par une écriture VALIDEE
        }

        Recharge recharge = new Recharge();
        recharge.setTransaction(txn);
        recharge.setPaymentMethod(method);
        recharge.setComment(StringUtils.hasText(req.comment()) ? req.comment().trim() : null);
        recharges.save(recharge);

        RechargeResponse response = toResponse(recharge, txn, account, card, customer, method, actor.username());
        idempotency.complete(idempotencyKey, 201, response);

        audit.record(AuditAction.RECHARGE_CREATED, "recharge", recharge.getId(), reference,
                "Recharge %s de %s FCFA sur %s (%s) — %s".formatted(reference, amount.toPlainString(),
                        card.getCardNo(), customer.getCustomerNo(), status),
                actor);

        if (pending) {
            notifications.staffAlert(NotificationType.RECHARGE_PENDING, NotificationSeverity.ALERTE,
                    "Recharge à valider",
                    "%s : %s FCFA sur %s (%s) attend une validation.".formatted(reference, amount.toPlainString(),
                            card.getCardNo(), customer.getCustomerNo()),
                    reference);
        }

        log.info("Recharge {} : {} FCFA sur {} ({}) — statut {}",
                reference, amount.toPlainString(), card.getCardNo(), customer.getCustomerNo(), status);
        return response;
    }

    // ------------------------------------------------------------------
    // Validation / refus d'une recharge en attente
    // ------------------------------------------------------------------

    @Transactional
    public RechargeResponse validate(Long rechargeId, Actor approver) {
        Recharge recharge = loadForDecision(rechargeId);
        Transaction txn = recharge.getTransaction();

        Account account = accounts.lockById(txn.getAccount().getId())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        Card card = txn.getCard();
        Customer customer = txn.getCustomer();

        require(card == null || card.getStatus() == CardStatus.ACTIVE, "La carte n'est plus active.");
        require(customer.getStatus() == CustomerStatus.ACTIF, "Le client n'est plus actif.");
        require(account.isActive(), "Le compte n'est plus actif.");

        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(txn.getAmount());

        txn.setBalanceBefore(MoneyRules.scaled(before));
        txn.setBalanceAfter(MoneyRules.scaled(after));
        txn.setStatus(TransactionStatus.VALIDEE);
        account.setBalance(after);

        audit.record(AuditAction.RECHARGE_VALIDATED, "recharge", recharge.getId(), txn.getReference(),
                "Recharge %s validée : solde %s → %s".formatted(txn.getReference(),
                        before.toPlainString(), after.toPlainString()),
                approver);
        log.info("Recharge {} validée par {} : solde {} → {}",
                txn.getReference(), approver.username(), before.toPlainString(), after.toPlainString());
        return toResponse(recharge, txn, account, card, customer, recharge.getPaymentMethod(), lookupUser(txn));
    }

    @Transactional
    public RechargeResponse reject(Long rechargeId, Actor approver, String reason) {
        Recharge recharge = loadForDecision(rechargeId);
        Transaction txn = recharge.getTransaction();

        txn.setStatus(TransactionStatus.REFUSEE);
        txn.setDescription(txn.getDescription() + " • REFUSÉE : " + reason.trim());
        recharge.setRejectedReason(reason.trim());

        audit.record(AuditAction.RECHARGE_REJECTED, "recharge", recharge.getId(), txn.getReference(),
                "Recharge %s refusée : %s".formatted(txn.getReference(), reason.trim()), approver);
        log.info("Recharge {} refusée par {} (motif : {})", txn.getReference(), approver.username(), reason.trim());
        return toResponse(recharge, txn, txn.getAccount(), txn.getCard(), txn.getCustomer(),
                recharge.getPaymentMethod(), lookupUser(txn));
    }

    private Recharge loadForDecision(Long rechargeId) {
        Recharge recharge = recharges.findDetailById(rechargeId)
                .orElseThrow(() -> ApiException.notFound("Recharge %d introuvable".formatted(rechargeId)));
        if (recharge.getTransaction().getStatus() != TransactionStatus.EN_ATTENTE) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cette recharge n'est plus en attente (statut : %s).".formatted(recharge.getTransaction().getStatus()));
        }
        return recharge;
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<RechargeResponse> search(TransactionStatus status, Long accountId, Long customerId, Long cardId,
                                         Instant from, Instant to, Pageable pageable) {
        return recharges.search(status, accountId, customerId, cardId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public RechargeResponse get(Long id) {
        Recharge recharge = recharges.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Recharge %d introuvable".formatted(id)));
        Transaction txn = recharge.getTransaction();
        return toResponse(recharge, txn, txn.getAccount(), txn.getCard(), txn.getCustomer(),
                recharge.getPaymentMethod(), lookupUser(txn));
    }

    // ------------------------------------------------------------------

    private void require(boolean condition, String message) {
        if (!condition) {
            throw ApiException.businessRule(message);
        }
    }

    private String lookupUser(Transaction txn) {
        return users.findById(txn.getUser().getId()).map(com.lubrimanager.user.User::getUsername).orElse("—");
    }

    private RechargeResponse toResponse(Recharge recharge, Transaction txn, Account account, Card card,
                                        Customer customer, PaymentMethod method, String operator) {
        return new RechargeResponse(
                recharge.getId(),
                txn.getReference(),
                txn.getId(),
                txn.getStatus(),
                account.getId(),
                customer.getId(),
                customer.getCustomerNo(),
                card != null ? card.getCardNo() : null,
                method.getCode(),
                txn.getAmount(),
                txn.getBalanceBefore(),
                txn.getBalanceAfter(),
                operator,
                recharge.getComment(),
                recharge.getCreatedAt());
    }
}
