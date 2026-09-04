package com.lubrimanager.inventory;

import com.lubrimanager.inventory.dto.InventoryMovementResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query(value = """
            select new com.lubrimanager.inventory.dto.InventoryMovementResponse(
                m.id, p.id, p.reference, p.name, m.type, m.quantity,
                m.stockBefore, m.stockAfter, m.reference, m.reason, u.username, m.createdAt)
            from InventoryMovement m
              join m.product p
              join m.user u
            where (:productId is null or p.id = :productId)
              and (:type is null or m.type = :type)
              and m.createdAt >= :from
              and m.createdAt < :to
            """,
            countQuery = """
            select count(m) from InventoryMovement m
              join m.product p
            where (:productId is null or p.id = :productId)
              and (:type is null or m.type = :type)
              and m.createdAt >= :from
              and m.createdAt < :to
            """)
    Page<InventoryMovementResponse> search(@Param("productId") Long productId,
                                           @Param("type") InventoryMovementType type,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to,
                                           Pageable pageable);

    @Query("""
            select new com.lubrimanager.inventory.dto.InventoryMovementResponse(
                m.id, p.id, p.reference, p.name, m.type, m.quantity,
                m.stockBefore, m.stockAfter, m.reference, m.reason, u.username, m.createdAt)
            from InventoryMovement m
              join m.product p
              join m.user u
            where p.id = :productId
            order by m.createdAt desc, m.id desc
            """)
    List<InventoryMovementResponse> findByProduct(@Param("productId") Long productId, Pageable pageable);

    @Query("""
            select m from InventoryMovement m
              join fetch m.product
              join fetch m.user
            where m.id = :id
            """)
    Optional<InventoryMovement> findDetailById(@Param("id") Long id);

    /** Somme des deltas d'un produit — contrôle d'invariant du cache de stock. */
    @Query("select coalesce(sum(m.quantity), 0) from InventoryMovement m where m.product.id = :productId")
    BigDecimal sumQuantityByProduct(@Param("productId") Long productId);
}
