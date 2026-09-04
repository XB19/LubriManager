package com.lubrimanager.user;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comportement de verrouillage (DEC-14), testé sans base ni contexte Spring. */
class UserAccountLockTest {

    private static final Duration LOCK = Duration.ofMinutes(15);
    private final Instant now = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void locksExactlyWhenMaxAttemptsIsReached() {
        User user = new User();

        user.registerFailedLogin(3, LOCK, now);
        assertThat(user.isLocked(now)).isFalse();

        user.registerFailedLogin(3, LOCK, now);
        assertThat(user.isLocked(now)).isFalse();

        user.registerFailedLogin(3, LOCK, now);
        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(now.plus(LOCK));
        assertThat(user.getFailedAttempts()).isZero(); // compteur remis à plat une fois verrouillé
    }

    @Test
    void lockExpiresAfterDuration() {
        User user = new User();
        for (int i = 0; i < 3; i++) {
            user.registerFailedLogin(3, LOCK, now);
        }

        assertThat(user.isLocked(now.plus(LOCK).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(now.plus(LOCK).plusSeconds(1))).isFalse();
    }

    @Test
    void successfulLoginClearsCounterAndLock() {
        User user = new User();
        user.registerFailedLogin(3, LOCK, now);
        user.registerFailedLogin(3, LOCK, now);

        user.registerSuccessfulLogin(now);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(now);
    }
}
