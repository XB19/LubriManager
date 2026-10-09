package com.lubrimanager.recharge;

import com.lubrimanager.account.Account;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.branch.BranchRepository;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.common.idempotency.IdempotencyService;
import com.lubrimanager.common.money.MoneyRules;
import com.lubrimanager.common.reference.ReferenceCounterService;
import com.lubrimanager.customer.Customer;
import com.lubrimanager.customer.CustomerRepository;
import com.lubrimanager.customer.CustomerStatus;
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
 * Recharges (crédit d'un compte, en espèces). Flux : sélection du client, saisie
 * du montant, validation immédiate.
 *
 * <p>Intégrité financière : l'écriture au grand livre et la mise à jour du solde
 * sont dans une seule transaction atomique ; verrou pessimiste sur la ligne
 * compte ; en-tête {@code Idempotency-Key} obligatoire (garde anti double-envoi).
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
    private final CustomerRepository customers;
    private final ReferenceCounterService references;
    private final IdempotencyService idempotency;
    private final BranchRepository branches;
    private final com.lubrimanager.user.UserRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public RechargeService(RechargeRepository recharges, TransactionRepository transactions,
                           AccountRepository accounts, CustomerRepository customers,
                           ReferenceCounterService references, IdempotencyService idempotency,
                           BranchRepository branches, com.lubrimanager.user.UserRepository users,
                           AuditService audit, Clock clock,
                           PlatformTransactionManager transactionManager) {
        this.recharges = recharges;
        this.transactions = transactions;
        this.accounts = accounts;
        this.customers = customers;
        this.references = references;
        this.idempotency = idempotency;
        this.branches = branches;
        this.users = users;
        this.audit = audit;
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
            return idempotency.lookup(idempotencyKey, hash)
                    .map(stored -> idempotency.deserialize(stored, RechargeResponse.class))
                    .orElseThrow(() -> concurrentDuplicate);
        }
    }

    private RechargeResponse doCreate(CreateRechargeRequest req, String idempotencyKey, String hash, Actor actor) {
        idempotency.reserve(idempotencyKey, ENDPOINT, hash); // saveAndFlush ⇒ doublon rejeté ici

        Customer customer = customers.findById(req.customerId())
                .orElseThrow(() -> ApiException.notFound("Client %d introuvable".formatted(req.customerId())));
        Account account = accounts.lockByCustomerId(customer.getId())
                .orElseThrow(() -> ApiException.notFound("Compte du client introuvable"));

        require(customer.getStatus() == CustomerStatus.ACTIF, "Le client n'est pas actif.");
        require(account.isActive(), "Le compte n'est pas actif.");

        BigDecimal amount = req.amount();
        require(MoneyRules.isStrictlyPositive(amount), "Le montant doit être positif.");
        require(MoneyRules.isWhole(amount), "Le montant doit être un nombre entier de FCFA.");

        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(amount);
        String reference = references.nextDated(REF_PREFIX, LocalDate.now(clock), REF_WIDTH);

        Transaction txn = new Transaction();
        txn.setReference(reference);
        txn.setType(TransactionType.RECHARGE);
        txn.setStatus(TransactionStatus.VALIDEE);
        txn.setAmount(MoneyRules.scaled(amount));
        txn.setAccount(account);
        txn.setCard(null);
        txn.setCustomer(customer);
        txn.setUser(users.getReferenceById(actor.id()));
        txn.setBranch(branches.getReferenceById(actor.branchId()));
        txn.setBalanceBefore(MoneyRules.scaled(before));
        txn.setBalanceAfter(MoneyRules.scaled(after));
        txn.setIdempotencyKey(idempotencyKey);
        txn.setDescription("Recharge %s • espèces%s".formatted(reference,
                StringUtils.hasText(req.comment()) ? " • " + req.comment().trim() : ""));
        transactions.save(txn);

        account.setBalance(after);

        Recharge recharge = new Recharge();
        recharge.setTransaction(txn);
        recharge.setComment(StringUtils.hasText(req.comment()) ? req.comment().trim() : null);
        recharges.save(recharge);

        RechargeResponse response = toResponse(recharge, txn, account, customer, actor.username());
        idempotency.complete(idempotencyKey, 201, response);

        audit.record(AuditAction.RECHARGE_CREATED, "recharge", recharge.getId(), reference,
                "Recharge %s de %s FCFA sur le compte de %s".formatted(reference, amount.toPlainString(),
                        customer.getCustomerNo()),
                actor);

        log.info("Recharge {} : {} FCFA sur le compte de {} — solde {} → {}",
                reference, amount.toPlainString(), customer.getCustomerNo(),
                before.toPlainString(), after.toPlainString());
        return response;
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
        return toResponse(recharge, txn, txn.getAccount(), txn.getCustomer(), lookupUser(txn));
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

    private RechargeResponse toResponse(Recharge recharge, Transaction txn, Account account, Customer customer,
                                        String operator) {
        return new RechargeResponse(
                recharge.getId(),
                txn.getReference(),
                txn.getId(),
                txn.getStatus(),
                account.getId(),
                customer.getId(),
                customer.getCustomerNo(),
                txn.getCard() != null ? txn.getCard().getCardNo() : null,
                txn.getAmount(),
                txn.getBalanceBefore(),
                txn.getBalanceAfter(),
                operator,
                recharge.getComment(),
                recharge.getCreatedAt());
    }
}
