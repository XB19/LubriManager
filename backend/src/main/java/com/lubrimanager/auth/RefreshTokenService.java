package com.lubrimanager.auth;

import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.security.JwtProperties;
import com.lubrimanager.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie des refresh tokens : émission, rotation et révocation.
 *
 * <p>Détection de rejeu : si un jeton déjà révoqué est présenté, toute la famille
 * de sessions de l'utilisateur est révoquée (vol probable).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final RefreshTokenReuseHandler reuseHandler;
    private final Clock clock;
    private final java.time.Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenReuseHandler reuseHandler,
                               JwtProperties jwtProperties, Clock clock) {
        this.repository = repository;
        this.reuseHandler = reuseHandler;
        this.clock = clock;
        this.ttl = jwtProperties.refreshTokenTtl();
    }

    /** Émet un nouveau jeton et renvoie sa valeur en clair (jamais restockée). */
    @Transactional
    public String issue(User user, String userAgent, String ip) {
        String raw = randomToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setCreatedAt(clock.instant());
        token.setExpiresAt(clock.instant().plus(ttl));
        token.setUserAgent(trim(userAgent, 200));
        token.setIp(trim(ip, 45));
        repository.save(token);
        return raw;
    }

    /** Valide l'ancien jeton, le révoque et en émet un nouveau. */
    @Transactional
    public Rotation rotate(String rawToken, String userAgent, String ip) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.tokenInvalid("Jeton de rafraîchissement inconnu."));

        if (current.isRevoked()) {
            Long userId = current.getUser().getId();
            log.warn("Réutilisation d'un refresh token révoqué pour l'utilisateur {} — révocation de toutes les sessions",
                    userId);
            // Transaction dédiée : la révocation doit survivre au rollback provoqué par l'exception ci-dessous.
            reuseHandler.revokeFamily(userId);
            throw ApiException.tokenInvalid("Session invalidée. Veuillez vous reconnecter.");
        }
        if (!current.isActive(now)) {
            throw ApiException.tokenInvalid("Session expirée. Veuillez vous reconnecter.");
        }

        User user = current.getUser();
        String newRaw = randomToken();
        current.setRevokedAt(now);
        current.setReplacedBy(hash(newRaw));

        RefreshToken next = new RefreshToken();
        next.setUser(user);
        next.setTokenHash(hash(newRaw));
        next.setCreatedAt(now);
        next.setExpiresAt(now.plus(ttl));
        next.setUserAgent(trim(userAgent, 200));
        next.setIp(trim(ip, 45));
        repository.save(next);

        return new Rotation(user, newRaw);
    }

    /** Révoque le jeton présenté s'il existe (déconnexion) — sans erreur sinon. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevokedAt(clock.instant());
            }
        });
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllActiveForUser(userId, clock.instant());
    }

    public record Rotation(User user, String rawToken) {
    }

    // -- utilitaires -----------------------------------------------------

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
