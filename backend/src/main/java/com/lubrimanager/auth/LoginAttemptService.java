package com.lubrimanager.auth;

import com.lubrimanager.security.LockoutProperties;
import com.lubrimanager.user.User;
import com.lubrimanager.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compteur d'échecs de connexion. Chaque enregistrement s'exécute dans SA PROPRE
 * transaction ({@code REQUIRES_NEW}) : la mise à jour du compteur doit être
 * persistée même si l'appelant lève ensuite une exception d'authentification.
 */
@Service
public class LoginAttemptService {

    private final UserRepository users;
    private final LockoutProperties lockout;
    private final Clock clock;

    public LoginAttemptService(UserRepository users, LockoutProperties lockout, Clock clock) {
        this.users = users;
        this.lockout = lockout;
        this.clock = clock;
    }

    /** @return {@code true} si ce nouvel échec a verrouillé le compte. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long userId) {
        Instant now = clock.instant();
        User user = users.findById(userId).orElseThrow();
        user.registerFailedLogin(lockout.maxFailedAttempts(), lockout.lockDuration(), now);
        return user.isLocked(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId) {
        User user = users.findById(userId).orElseThrow();
        user.registerSuccessfulLogin(clock.instant());
    }
}
