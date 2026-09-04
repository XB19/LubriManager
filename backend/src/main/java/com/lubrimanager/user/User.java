package com.lubrimanager.user;

import com.lubrimanager.branch.Branch;
import com.lubrimanager.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compte applicatif. Jamais supprimé : on le désactive ({@code active = false}),
 * ce qui révoque aussi ses sessions (RG-17).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "app_user")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    // EAGER assumé : l'agence est petite, toujours nécessaire (jeton, profil), et
    // les listes d'utilisateurs sont paginées et de faible volume.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(int maxAttempts, java.time.Duration lockDuration, Instant now) {
        this.failedAttempts++;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockDuration);
            this.failedAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }
}
