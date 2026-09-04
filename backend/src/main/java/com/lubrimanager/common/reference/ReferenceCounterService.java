package com.lubrimanager.common.reference;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attribue les numéros séquentiels des références métier.
 *
 * <p>{@code MANDATORY} : doit être appelé à l'intérieur de la transaction de
 * l'opération porteuse. Si celle-ci échoue, l'incrément est annulé — le numéro
 * n'est pas consommé.
 */
@Service
public class ReferenceCounterService {

    private final ReferenceCounterRepository repository;

    public ReferenceCounterService(ReferenceCounterRepository repository) {
        this.repository = repository;
    }

    /** Référence simple, ex. {@code CLI-000001}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String prefix, int width) {
        return References.format(prefix, nextValue(prefix), width);
    }

    /** Référence datée, ex. {@code RECH-20260903-000001} (séquence propre à chaque jour). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextDated(String prefix, LocalDate date, int width) {
        String scope = References.datedScope(prefix, date);
        return References.formatDated(prefix, date, nextValue(scope), width);
    }

    private long nextValue(String scope) {
        ReferenceCounter counter = repository.lockByScope(scope).orElse(null);
        if (counter == null) {
            repository.ensureScope(scope);
            counter = repository.lockByScope(scope)
                    .orElseThrow(() -> new IllegalStateException("Portée de compteur introuvable : " + scope));
        }
        return counter.increment();
    }
}
