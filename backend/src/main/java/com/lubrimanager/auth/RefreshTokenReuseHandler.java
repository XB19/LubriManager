package com.lubrimanager.auth;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Révoque toutes les sessions actives d'un utilisateur lorsqu'un refresh token
 * déjà révoqué est rejoué (vol probable). Exécuté dans une transaction dédiée
 * afin d'être validé avant que l'appelant ne rejette la requête.
 */
@Component
public class RefreshTokenReuseHandler {

    private final RefreshTokenRepository repository;
    private final Clock clock;

    public RefreshTokenReuseHandler(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(Long userId) {
        repository.revokeAllActiveForUser(userId, clock.instant());
    }
}
