package com.lubrimanager.account;

import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.branch.BranchRepository;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.common.money.MoneyRules;
import com.lubrimanager.common.reference.ReferenceCounterService;
import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import com.lubrimanager.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Ajustement manuel de solde (RG-13) — réservé à l'ADMIN, motif obligatoire,
 * écriture {@code AJUSTEMENT} au grand livre et entrée d'audit. Ne peut pas rendre
 * le solde négatif (DEC-08).
 */
@Service
public class AccountAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(AccountAdjustmentService.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final ReferenceCounterService references;
    private final UserRepository users;
    private final BranchRepository branches;
    private final AuditService audit;
    private final Clock clock;

    public AccountAdjustmentService(AccountRepository accounts, TransactionRepository transactions,
                                    ReferenceCounterService references, UserRepository users,
                                    BranchRepository branches, AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.references = references;
        this.users = users;
        this.branches = branches;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Account adjust(Long accountId, BigDecimal amount, String reason, Actor actor) {
        if (!StringUtils.hasText(reason)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Un motif est obligatoire.");
        }
        if (amount == null || amount.signum() == 0) {
            throw ApiException.businessRule("Le montant de l'ajustement ne peut pas être nul.");
        }
        if (!MoneyRules.isWhole(amount)) {
            throw ApiException.businessRule("Le montant doit être un nombre entier de FCFA.");
        }

        Account account = accounts.lockById(accountId)
                .orElseThrow(() -> ApiException.notFound("Compte %d introuvable".formatted(accountId)));

        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(amount);
        if (after.signum() < 0) {
            throw ApiException.businessRule("Cet ajustement rendrait le solde négatif.");
        }
        account.setBalance(after);

        String reference = references.nextDated("AJUS", LocalDate.now(clock), 6);
        Transaction txn = new Transaction();
        txn.setReference(reference);
        txn.setType(TransactionType.AJUSTEMENT);
        txn.setStatus(TransactionStatus.VALIDEE);
        txn.setAmount(MoneyRules.scaled(amount));
        txn.setAccount(account);
        txn.setCustomer(account.getCustomer());
        txn.setUser(users.getReferenceById(actor.id()));
        txn.setBranch(branches.getReferenceById(actor.branchId()));
        txn.setBalanceBefore(MoneyRules.scaled(before));
        txn.setBalanceAfter(MoneyRules.scaled(after));
        txn.setDescription("Ajustement manuel %s : %s".formatted(reference, reason.trim()));
        transactions.save(txn);

        audit.recordChange(AuditAction.ACCOUNT_ADJUSTED, "account", accountId, reference,
                "Ajustement de %s FCFA sur le compte %d : %s".formatted(amount.toPlainString(), accountId, reason.trim()),
                Map.of("balance", before.toPlainString()), Map.of("balance", after.toPlainString()), actor);

        log.info("Ajustement {} compte {} par {} : {} → {} ({})", reference, accountId, actor.username(),
                before.toPlainString(), after.toPlainString(), reason.trim());
        return account;
    }
}
