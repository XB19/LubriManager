package com.lubrimanager.security;

import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.user.Role;
import com.lubrimanager.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Génère et vérifie les jetons d'accès (JWT HS256, courte durée). Le refresh token
 * n'est pas un JWT : c'est un jeton opaque géré par {@link com.lubrimanager.auth.RefreshTokenService}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final java.time.Duration accessTtl;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = props.issuer();
        this.accessTtl = props.accessTokenTtl();
        this.clock = clock;
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("bid", user.getBranch().getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** @throws ApiException {@code TOKEN_EXPIRED} ou {@code TOKEN_INVALID}. */
    public AccessClaims parse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AccessClaims(
                    c.getSubject(),
                    c.get("uid", Number.class).longValue(),
                    Role.valueOf(c.get("role", String.class)),
                    c.get("bid", Number.class).longValue(),
                    c.getId(),
                    c.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
    }

    public record AccessClaims(String username, Long userId, Role role, Long branchId, String jti, Instant expiresAt) {
    }
}
