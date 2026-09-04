package com.lubrimanager.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entrée de piste d'audit — <strong>en ajout seul</strong> (§25, §26). Conserve
 * qui a fait quoi, quand, depuis où, et l'ancienne / la nouvelle valeur lorsque
 * pertinent (JSON). Rétention ≥ 5 ans (DEC-16).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 60)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 40)
    private String reference;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
