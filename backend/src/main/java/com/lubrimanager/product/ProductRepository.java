package com.lubrimanager.product;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByReference(String reference);

    boolean existsByReference(String reference);

    /** Verrou pessimiste : sérialise les débits carte portant sur un même produit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(@Param("id") Long id);

    /** {@code pattern} est un motif LIKE déjà minusculisé ({@code %terme%}) ou {@code %} pour « tout ». */
    @Query("""
            select p from Product p
            where lower(p.reference) like :pattern
               or lower(p.name) like :pattern
               or lower(p.brand) like :pattern
            """)
    Page<Product> search(@Param("pattern") String pattern, Pageable pageable);
}
