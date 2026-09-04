package com.lubrimanager.auth;

import com.lubrimanager.audit.AuditAction;
import com.lubrimanager.audit.AuditService;
import com.lubrimanager.auth.dto.MeResponse;
import com.lubrimanager.auth.dto.TokenResponse;
import com.lubrimanager.common.Actor;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.security.JwtService;
import com.lubrimanager.user.User;
import com.lubrimanager.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration de la connexion. Volontairement <em>non</em> transactionnelle au
 * niveau de la méthode : le comptage des échecs (verrouillage, DEC-14) et
 * l'émission des jetons sont des sous-opérations indépendantes qui ne doivent pas
 * être annulées ensemble si l'une échoue.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final AuditService audit;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, LoginAttemptService loginAttempts, AuditService audit,
                       Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.clock = clock;
    }

    public TokenResponse login(String usernameOrEmail, String rawPassword, String userAgent, String ip) {
        Instant now = clock.instant();
        User user = users.findByUsernameOrEmail(usernameOrEmail.trim())
                .orElseThrow(ApiException::authenticationFailed);

        if (user.isLocked(now)) {
            throw ApiException.accountLocked();
        }
        // Un compte désactivé échoue comme des identifiants invalides (pas de divulgation).
        if (!user.isActive()) {
            throw ApiException.authenticationFailed();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            boolean nowLocked = loginAttempts.recordFailure(user.getId());
            log.info("Échec de connexion pour « {} » (verrouillé={})", user.getUsername(), nowLocked);
            audit.record(AuditAction.USER_LOGIN_FAILURE, "user", null, null,
                    "Échec de connexion pour « %s »%s".formatted(user.getUsername(), nowLocked ? " — compte verrouillé" : ""),
                    new Actor(user.getId(), user.getUsername(), null));
            throw nowLocked ? ApiException.accountLocked() : ApiException.authenticationFailed();
        }

        loginAttempts.recordSuccess(user.getId());
        Actor actor = new Actor(user.getId(), user.getUsername(), user.getBranch().getId());
        audit.record(AuditAction.USER_LOGIN_SUCCESS, "user", user.getId(), null, "Connexion réussie", actor);
        String access = jwtService.generateAccessToken(user);
        String refresh = refreshTokens.issue(user, userAgent, ip);
        return TokenResponse.bearer(access, jwtService.accessTtlSeconds(), refresh, MeResponse.from(user));
    }

    public TokenResponse refresh(String rawRefreshToken, String userAgent, String ip) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, userAgent, ip);
        User user = rotation.user();
        if (!user.isActive()) {
            refreshTokens.revokeAllForUser(user.getId());
            throw ApiException.tokenInvalid("Compte désactivé.");
        }
        String access = jwtService.generateAccessToken(user);
        return TokenResponse.bearer(access, jwtService.accessTtlSeconds(), rotation.rawToken(), MeResponse.from(user));
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        return users.findById(userId)
                .map(MeResponse::from)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }
}
