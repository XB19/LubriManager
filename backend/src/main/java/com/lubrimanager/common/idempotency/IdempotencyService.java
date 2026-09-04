package com.lubrimanager.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotence des opérations sensibles (RG-09).
 *
 * <ul>
 *   <li>{@link #lookup} — au tout début : renvoie la réponse mémorisée si la clé a
 *       déjà servi ; 409 si la même clé est réutilisée avec une charge différente.</li>
 *   <li>{@link #reserve} — pose la clé ({@code saveAndFlush} ⇒ doublon rejeté
 *       immédiatement), dans la transaction de l'opération.</li>
 *   <li>{@link #complete} — mémorise la réponse une fois l'opération réussie.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String hash(String endpoint, Object requestBody) {
        try {
            String canonical = endpoint + '|' + objectMapper.writeValueAsString(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Calcul d'empreinte d'idempotence impossible", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<StoredResponse> lookup(String key, String requestHash) {
        IdempotencyRecord record = repository.findById(key).orElse(null);
        if (record == null) {
            return Optional.empty();
        }
        if (!record.getRequestHash().equals(requestHash)) {
            throw ApiException.conflict("Cette clé d'idempotence a déjà été utilisée pour une autre requête.");
        }
        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Une requête identique est déjà en cours de traitement. Réessayez dans un instant.");
        }
        return Optional.of(new StoredResponse(record.getHttpStatus(), record.getResponseBody()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(String key, String endpoint, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setKey(key);
        record.setEndpoint(endpoint);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        record.setCreatedAt(clock.instant());
        repository.saveAndFlush(record); // matérialise tout de suite la contrainte d'unicité
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String key, int httpStatus, Object responseBody) {
        IdempotencyRecord record = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Clé d'idempotence non réservée : " + key));
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setHttpStatus(httpStatus);
        try {
            record.setResponseBody(objectMapper.writeValueAsString(responseBody));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation de la réponse idempotente impossible", e);
        }
    }

    public <T> T deserialize(StoredResponse stored, Class<T> type) {
        try {
            return objectMapper.readValue(stored.body(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Relecture de la réponse idempotente impossible", e);
        }
    }

    public record StoredResponse(Integer httpStatus, String body) {
    }
}
