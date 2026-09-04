package com.lubrimanager.audit.dto;

import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditLog;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long userId,
        String username,
        AuditAction action,
        String entityType,
        Long entityId,
        String reference,
        String summary,
        String oldValue,
        String newValue,
        String ip,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getUserId(), a.getUsername(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getReference(), a.getSummary(),
                a.getOldValue(), a.getNewValue(), a.getIp(), a.getCreatedAt());
    }
}
