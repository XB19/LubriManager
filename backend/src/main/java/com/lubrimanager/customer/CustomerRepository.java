package com.lubrimanager.customer;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerNo(String customerNo);

    boolean existsByCustomerNo(String customerNo);

    /**
     * Recherche globale (§30) : {@code q} est comparé au n° client, au nom, à la
     * raison sociale, au téléphone et à l'e-mail. Filtres optionnels type / statut.
     */
    /** {@code pattern} : motif LIKE déjà minusculisé ({@code %terme%}) ou {@code %} pour « tout ». */
    @Query("""
            select c from Customer c
            where (lower(c.customerNo) like :pattern
                   or lower(c.fullName) like :pattern
                   or (c.companyName is not null and lower(c.companyName) like :pattern)
                   or (c.phone is not null and lower(c.phone) like :pattern)
                   or (c.email is not null and lower(c.email) like :pattern))
              and (:type is null or c.type = :type)
              and (:status is null or c.status = :status)
            """)
    Page<Customer> search(@Param("pattern") String pattern,
                          @Param("type") CustomerType type,
                          @Param("status") CustomerStatus status,
                          Pageable pageable);
}
