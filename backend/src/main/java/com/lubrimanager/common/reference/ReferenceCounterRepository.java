package com.lubrimanager.common.reference;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferenceCounterRepository extends JpaRepository<ReferenceCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReferenceCounter c where c.scope = :scope")
    Optional<ReferenceCounter> lockByScope(@Param("scope") String scope);

    /** Crée la portée si elle n'existe pas encore — idempotent, sans erreur en cas de concurrence. */
    @Modifying
    @Query(value = """
            insert into reference_counter(scope, next_value) values (:scope, 0)
            on conflict (scope) do nothing
            """, nativeQuery = true)
    void ensureScope(@Param("scope") String scope);
}
