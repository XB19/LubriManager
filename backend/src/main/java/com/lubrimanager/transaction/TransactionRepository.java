package com.lubrimanager.transaction;

import com.lubrimanager.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReference(String reference);

    @Query(value = """
            select new com.lubrimanager.transaction.dto.TransactionResponse(
                t.id, t.reference, t.type, t.status, t.amount,
                t.balanceBefore, t.balanceAfter,
                t.account.id, t.customer.id, t.customer.customerNo, t.customer.fullName,
                c.cardNo, t.user.username, t.description, t.createdAt)
            from Transaction t
              left join t.card c
            where (:type is null or t.type = :type)
              and (:status is null or t.status = :status)
              and (:accountId is null or t.account.id = :accountId)
              and (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and t.createdAt >= :from
              and t.createdAt < :to
            """,
            countQuery = """
            select count(t) from Transaction t
              left join t.card c
            where (:type is null or t.type = :type)
              and (:status is null or t.status = :status)
              and (:accountId is null or t.account.id = :accountId)
              and (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and t.createdAt >= :from
              and t.createdAt < :to
            """)
    Page<TransactionResponse> search(@Param("type") TransactionType type,
                                     @Param("status") TransactionStatus status,
                                     @Param("accountId") Long accountId,
                                     @Param("customerId") Long customerId,
                                     @Param("cardId") Long cardId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);

    /** Somme des écritures VALIDEE d'un compte — sert au contrôle d'invariant (RG-00). */
    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.account.id = :accountId and t.status = :status
            """)
    BigDecimal sumByAccountAndStatus(@Param("accountId") Long accountId, @Param("status") TransactionStatus status);
}
