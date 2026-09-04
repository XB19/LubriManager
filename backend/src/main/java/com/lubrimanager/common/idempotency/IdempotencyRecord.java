package com.lubrimanager.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trace d'une opération sensible identifiée par une {@code Idempotency-Key}. La
 * clé primaire porte la contrainte d'unicité qui bloque les doublons (RG-09).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "idem_key", length = 80)
    private String key;

    @Column(nullable = false, length = 120)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
