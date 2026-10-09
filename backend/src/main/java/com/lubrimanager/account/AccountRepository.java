package com.lubrimanager.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByCustomerId(Long customerId);

    boolean existsByCustomerId(Long customerId);

    /** Verrou pessimiste (SELECT … FOR UPDATE) sur la ligne compte : sérialise les écritures de solde. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") Long id);

    /** Verrou pessimiste sur le compte d'un client (relation 1–1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.customer.id = :customerId")
    Optional<Account> lockByCustomerId(@Param("customerId") Long customerId);
}
