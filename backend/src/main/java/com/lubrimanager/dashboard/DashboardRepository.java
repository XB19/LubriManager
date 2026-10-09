package com.lubrimanager.dashboard;

import com.lubrimanager.card.CardStatus;
import com.lubrimanager.customer.CustomerStatus;
import com.lubrimanager.dashboard.dto.TopCustomerRow;
import com.lubrimanager.dashboard.dto.TopProductRow;
import com.lubrimanager.dashboard.dto.UserActivityRow;
import com.lubrimanager.transaction.Transaction;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Requêtes d'agrégation lecture seule pour le tableau de bord (§29). */
public interface DashboardRepository extends Repository<Transaction, Long> {

    @Query("select count(c) from Customer c")
    long countCustomers();

    @Query("select count(c) from Customer c where c.status <> :excluded")
    long countCustomersExcludingStatus(@Param("excluded") CustomerStatus excluded);

    @Query("select count(k) from Card k where k.status = :status")
    long countCardsByStatus(@Param("status") CardStatus status);

    @Query("select count(k) from Card k")
    long countCards();

    @Query("select count(t) from Transaction t where t.type = :type and t.status = :status")
    long countTransactions(@Param("type") TransactionType type, @Param("status") TransactionStatus status);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.type = :type and t.status = :status
              and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal sumAmount(@Param("type") TransactionType type, @Param("status") TransactionStatus status,
                         @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(t) from Transaction t
            where t.type = :type and t.status = :status
              and t.createdAt >= :from and t.createdAt < :to
            """)
    long countAmount(@Param("type") TransactionType type, @Param("status") TransactionStatus status,
                     @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select new com.lubrimanager.dashboard.dto.TopProductRow(
                pi.product.id, pi.productReference, pi.productName,
                sum(pi.quantity), sum(pi.lineTotal))
            from PurchaseItem pi
              join pi.purchase p
              join p.transaction t
            where t.status = :validated and p.createdAt >= :from and p.createdAt < :to
            group by pi.product.id, pi.productReference, pi.productName
            order by sum(pi.quantity) desc
            """)
    List<TopProductRow> topProducts(@Param("validated") TransactionStatus validated,
                                    @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select new com.lubrimanager.dashboard.dto.TopCustomerRow(
                t.customer.id, t.customer.customerNo, t.customer.fullName,
                coalesce(sum(-t.amount), 0), count(t))
            from Transaction t
            where t.type = :achat and t.status = :validated
              and t.createdAt >= :from and t.createdAt < :to
            group by t.customer.id, t.customer.customerNo, t.customer.fullName
            order by sum(-t.amount) desc
            """)
    List<TopCustomerRow> topCustomers(@Param("achat") TransactionType achat, @Param("validated") TransactionStatus validated,
                                      @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select new com.lubrimanager.dashboard.dto.UserActivityRow(
                t.user.id, t.user.username,
                sum(case when t.type = :achat then 1L else 0L end),
                coalesce(sum(case when t.type = :achat then -t.amount else 0 end), 0),
                sum(case when t.type = :recharge then 1L else 0L end),
                coalesce(sum(case when t.type = :recharge then t.amount else 0 end), 0))
            from Transaction t
            where t.status = :validated and t.createdAt >= :from and t.createdAt < :to
            group by t.user.id, t.user.username
            order by t.user.username
            """)
    List<UserActivityRow> userActivity(@Param("achat") TransactionType achat, @Param("recharge") TransactionType recharge,
                                       @Param("validated") TransactionStatus validated,
                                       @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(-t.amount), 0) from Transaction t
            where t.user.id = :userId and t.type = :achat and t.status = :validated
              and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal userSalesAmount(@Param("userId") Long userId, @Param("achat") TransactionType achat,
                               @Param("validated") TransactionStatus validated,
                               @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(t) from Transaction t
            where t.user.id = :userId and t.type = :type and t.status = :validated
              and t.createdAt >= :from and t.createdAt < :to
            """)
    long userTransactionCount(@Param("userId") Long userId, @Param("type") TransactionType type,
                              @Param("validated") TransactionStatus validated,
                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.user.id = :userId and t.type = :recharge and t.status = :validated
              and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal userRechargeAmount(@Param("userId") Long userId, @Param("recharge") TransactionType recharge,
                                  @Param("validated") TransactionStatus validated,
                                  @Param("from") Instant from, @Param("to") Instant to);
}
