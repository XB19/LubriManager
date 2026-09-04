package com.lubrimanager.transaction;

import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.transaction.dto.TransactionResponse;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation du grand livre. L'ajout d'écritures est du ressort des services
 * métier (recharge, vente…), en transactions atomiques.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactions;

    public TransactionService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    public Page<TransactionResponse> search(TransactionType type, TransactionStatus status, Long accountId,
                                            Long customerId, Long cardId, Instant from, Instant to,
                                            Pageable pageable) {
        return transactions.search(type, status, accountId, customerId, cardId, from, to, pageable);
    }

    public TransactionResponse get(Long id) {
        return transactions.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> ApiException.notFound("Transaction %d introuvable".formatted(id)));
    }

    public TransactionResponse getByReference(String reference) {
        return transactions.findByReference(reference)
                .map(TransactionResponse::from)
                .orElseThrow(() -> ApiException.notFound("Transaction %s introuvable".formatted(reference)));
    }
}
