package com.lubrimanager.purchase;

import com.lubrimanager.purchase.dto.PurchaseSummaryResponse;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Query("""
            select p from Purchase p
              join fetch p.transaction t
              join fetch t.account
              join fetch t.customer
              join fetch t.user
              left join fetch t.card
            where p.id = :id
            """)
    Optional<Purchase> findDetailById(@Param("id") Long id);

    @Query("""
            select p from Purchase p
              join fetch p.transaction t
              join fetch t.account
              join fetch t.customer
              join fetch t.user
              left join fetch t.card
            where t.reference = :reference
            """)
    Optional<Purchase> findDetailByReference(@Param("reference") String reference);

    Optional<Purchase> findByTransactionId(Long transactionId);

    @Query(value = """
            select new com.lubrimanager.purchase.dto.PurchaseSummaryResponse(
                p.id, t.reference, t.status, t.customer.id, t.customer.customerNo,
                c.cardNo, p.total, u.username, p.createdAt)
            from Purchase p
              join p.transaction t
              join t.user u
              left join t.card c
            where (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and p.createdAt >= :from
              and p.createdAt < :to
            """,
            countQuery = """
            select count(p) from Purchase p
              join p.transaction t
              left join t.card c
            where (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and p.createdAt >= :from
              and p.createdAt < :to
            """)
    Page<PurchaseSummaryResponse> search(@Param("customerId") Long customerId,
                                         @Param("cardId") Long cardId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to,
                                         Pageable pageable);
}
