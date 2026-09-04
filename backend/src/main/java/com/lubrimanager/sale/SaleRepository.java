package com.lubrimanager.sale;

import com.lubrimanager.sale.dto.SaleSummaryResponse;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("""
            select s from Sale s
              join fetch s.transaction t
              join fetch t.account
              join fetch t.customer
              join fetch t.user
              left join fetch t.card
            where s.id = :id
            """)
    Optional<Sale> findDetailById(@Param("id") Long id);

    @Query("""
            select s from Sale s
              join fetch s.transaction t
              join fetch t.account
              join fetch t.customer
              join fetch t.user
              left join fetch t.card
            where t.reference = :reference
            """)
    Optional<Sale> findDetailByReference(@Param("reference") String reference);

    Optional<Sale> findByTransactionId(Long transactionId);

    @Query(value = """
            select new com.lubrimanager.sale.dto.SaleSummaryResponse(
                s.id, t.reference, t.status, t.customer.id, t.customer.customerNo,
                c.cardNo, s.total, u.username, s.createdAt)
            from Sale s
              join s.transaction t
              join t.user u
              left join t.card c
            where (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and s.createdAt >= :from
              and s.createdAt < :to
            """,
            countQuery = """
            select count(s) from Sale s
              join s.transaction t
              left join t.card c
            where (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and s.createdAt >= :from
              and s.createdAt < :to
            """)
    Page<SaleSummaryResponse> search(@Param("customerId") Long customerId,
                                     @Param("cardId") Long cardId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);
}
