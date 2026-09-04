package com.lubrimanager.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.AuditContext;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écriture de la piste d'audit.
 *
 * <p>Propagation {@code REQUIRED} : appelé <em>dans</em> la transaction d'une
 * opération financière, l'audit est validé (ou annulé) avec elle — il en fait
 * partie (§16). Appelé hors transaction (connexion, refus de vente), il s'exécute
 * dans sa propre transaction et survit donc à l'exception qui suit.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditAction action, String entityType, Long entityId, String reference,
                       String summary, Actor actor) {
        recordChange(action, entityType, entityId, reference, summary, null, null, actor);
    }

    @Transactional
    public void recordChange(AuditAction action, String entityType, Long entityId, String reference,
                             String summary, Map<String, ?> oldValue, Map<String, ?> newValue, Actor actor) {
        AuditContext.Info ctx = AuditContext.get();
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setReference(reference);
        entry.setSummary(truncate(summary));
        entry.setOldValue(toJson(oldValue));
        entry.setNewValue(toJson(newValue));
        entry.setIp(ctx.ip());
        entry.setUserAgent(ctx.userAgent());
        if (actor != null) {
            entry.setUserId(actor.id());
            entry.setUsername(actor.username());
            entry.setBranchId(actor.branchId());
        }
        entry.setCreatedAt(clock.instant());
        repository.save(entry);
    }

    private String toJson(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Sérialisation d'une valeur d'audit impossible : {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
