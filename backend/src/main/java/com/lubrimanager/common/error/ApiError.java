package com.lubrimanager.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Corps de réponse unique pour toutes les erreurs de l'API.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-03T14:32:07Z",
 *   "status": 422,
 *   "code": "BUSINESS_RULE",
 *   "message": "Le solde de la carte ne permet pas cette vente.",
 *   "path": "/api/sales",
 *   "requestId": "b1f2…",
 *   "errors": [ { "field": "amount", "message": "doit être positif" } ]
 * }
 * </pre>
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> errors) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path, String requestId) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, requestId, null);
    }

    public static ApiError of(ErrorCode code, String message, String path, String requestId, List<FieldViolation> errors) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, requestId,
                errors == null || errors.isEmpty() ? null : errors);
    }
}
