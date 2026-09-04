package com.lubrimanager.card;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByCardNo(String cardNo);

    boolean existsByCardNo(String cardNo);

    boolean existsByExternalNoIgnoreCase(String externalNo);

    List<Card> findByCustomerIdOrderByIssuedAtDesc(Long customerId);

    @Query("""
            select c from Card c
            where (lower(c.cardNo) like :pattern
                   or (c.externalNo is not null and lower(c.externalNo) like :pattern)
                   or lower(c.customer.customerNo) like :pattern
                   or lower(c.customer.fullName) like :pattern)
              and (:status is null or c.status = :status)
              and (:customerId is null or c.customer.id = :customerId)
            """)
    Page<Card> search(@Param("pattern") String pattern,
                      @Param("status") CardStatus status,
                      @Param("customerId") Long customerId,
                      Pageable pageable);

    /** Passe à EXPIREE les cartes dont la date d'expiration est dépassée (RG-18). */
    @Modifying
    @Query("""
            update Card c
               set c.status = :expired
             where c.status in :openStatuses
               and c.expiresAt is not null
               and c.expiresAt < :today
            """)
    int expireOverdue(@Param("expired") CardStatus expired,
                      @Param("openStatuses") Collection<CardStatus> openStatuses,
                      @Param("today") LocalDate today);
}
