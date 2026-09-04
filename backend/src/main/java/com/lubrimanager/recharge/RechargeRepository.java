package com.lubrimanager.recharge;

import com.lubrimanager.recharge.dto.RechargeResponse;
import com.lubrimanager.transaction.TransactionStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RechargeRepository extends JpaRepository<Recharge, Long> {

    @Query("select r from Recharge r join fetch r.transaction join fetch r.paymentMethod where r.id = :id")
    Optional<Recharge> findDetailById(@Param("id") Long id);

    @Query(value = """
            select new com.lubrimanager.recharge.dto.RechargeResponse(
                r.id, t.reference, t.id, t.status,
                t.account.id, t.customer.id, t.customer.customerNo, c.cardNo,
                pm.code, t.amount, t.balanceBefore, t.balanceAfter,
                u.username, r.comment, r.createdAt)
            from Recharge r
              join r.transaction t
              join r.paymentMethod pm
              join t.user u
              left join t.card c
            where (:status is null or t.status = :status)
              and (:accountId is null or t.account.id = :accountId)
              and (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and r.createdAt >= :from
              and r.createdAt < :to
            """,
            countQuery = """
            select count(r) from Recharge r
              join r.transaction t
              left join t.card c
            where (:status is null or t.status = :status)
              and (:accountId is null or t.account.id = :accountId)
              and (:customerId is null or t.customer.id = :customerId)
              and (:cardId is null or c.id = :cardId)
              and r.createdAt >= :from
              and r.createdAt < :to
            """)
    Page<RechargeResponse> search(@Param("status") TransactionStatus status,
                                  @Param("accountId") Long accountId,
                                  @Param("customerId") Long customerId,
                                  @Param("cardId") Long cardId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);
}
