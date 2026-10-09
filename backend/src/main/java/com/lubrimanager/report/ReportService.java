package com.lubrimanager.report;

import com.lubrimanager.audit.AuditLog;
import com.lubrimanager.audit.AuditLogRepository;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.purchase.PurchaseRepository;
import com.lubrimanager.purchase.dto.PurchaseSummaryResponse;
import com.lubrimanager.recharge.RechargeRepository;
import com.lubrimanager.recharge.dto.RechargeResponse;
import com.lubrimanager.report.dto.ReportData;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionType;
import com.lubrimanager.transaction.dto.TransactionResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Génération des rapports (§32). Réutilise les requêtes existantes ; limite le
 * volume pour rester en mémoire (au-delà, affiner la période).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int MAX_ROWS = 20_000;

    private final PurchaseRepository purchases;
    private final RechargeRepository recharges;
    private final TransactionRepository transactions;
    private final AuditLogRepository auditLogs;

    public ReportService(PurchaseRepository purchases, RechargeRepository recharges,
                         TransactionRepository transactions, AuditLogRepository auditLogs) {
        this.purchases = purchases;
        this.recharges = recharges;
        this.transactions = transactions;
        this.auditLogs = auditLogs;
    }

    public ReportData generate(ReportType type, Instant from, Instant to) {
        return switch (type) {
            case PURCHASES -> purchasesReport(from, to);
            case RECHARGES -> rechargesReport(from, to);
            case TRANSACTIONS -> transactionsReport(from, to, null);
            case CANCELLATIONS -> transactionsReport(from, to, TransactionType.ANNULATION);
            case AUDIT -> auditReport(from, to);
        };
    }

    private ReportData purchasesReport(Instant from, Instant to) {
        Page<PurchaseSummaryResponse> page = purchases.search(null, null, from, to, sorted());
        guard(page.getTotalElements());
        List<List<Object>> rows = page.getContent().stream().map(s -> row(
                s.reference(), s.status(), s.customerNo(), s.cardNo(), s.total(), s.operator(), s.createdAt())).toList();
        return new ReportData("Rapport des débits carte",
                List.of("Référence", "Statut", "N° client", "Carte", "Total", "Opérateur", "Date"), rows);
    }

    private ReportData rechargesReport(Instant from, Instant to) {
        Page<RechargeResponse> page = recharges.search(null, null, null, null, from, to, sorted());
        guard(page.getTotalElements());
        List<List<Object>> rows = page.getContent().stream().map(r -> row(
                r.reference(), r.status(), r.customerNo(), r.amount(),
                r.balanceAfter(), r.operator(), r.createdAt())).toList();
        return new ReportData("Rapport des recharges",
                List.of("Référence", "Statut", "N° client", "Montant", "Solde après",
                        "Opérateur", "Date"), rows);
    }

    private ReportData transactionsReport(Instant from, Instant to, TransactionType type) {
        Page<TransactionResponse> page = transactions.search(type, null, null, null, null, from, to, sorted());
        guard(page.getTotalElements());
        List<List<Object>> rows = page.getContent().stream().map(t -> row(
                t.reference(), t.type(), t.status(), t.amount(), t.balanceBefore(), t.balanceAfter(),
                t.customerNo(), t.cardNo(), t.operator(), t.createdAt())).toList();
        String title = type == null ? "Rapport des transactions" : "Rapport des annulations";
        return new ReportData(title,
                List.of("Référence", "Type", "Statut", "Montant", "Solde avant", "Solde après", "N° client",
                        "Carte", "Opérateur", "Date"), rows);
    }

    private ReportData auditReport(Instant from, Instant to) {
        Page<AuditLog> page = auditLogs.search(null, null, null, null, from, to,
                PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        guard(page.getTotalElements());
        List<List<Object>> rows = page.getContent().stream().map(a -> row(
                a.getCreatedAt(), a.getUsername(), a.getAction(), a.getEntityType(), a.getReference(),
                a.getSummary(), a.getIp())).toList();
        return new ReportData("Journal d'audit",
                List.of("Date", "Utilisateur", "Action", "Entité", "Référence", "Résumé", "IP"), rows);
    }

    private static PageRequest sorted() {
        return PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private static List<Object> row(Object... cells) {
        return java.util.Arrays.asList(cells);
    }

    private void guard(long total) {
        if (total > MAX_ROWS) {
            throw ApiException.businessRule(
                    "Le rapport contient %d lignes (max %d). Affinez la période.".formatted(total, MAX_ROWS));
        }
    }
}
