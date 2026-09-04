package com.lubrimanager.security;

import com.lubrimanager.branch.Branch;
import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.user.Role;
import com.lubrimanager.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests unitaires purs (aucun contexte Spring, aucun Docker). */
class JwtServiceTest {

    private static final String SECRET = "unit-secret-unit-secret-unit-secret-0123456789";
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);

    private JwtService serviceWithTtl(Duration ttl) {
        return new JwtService(new JwtProperties("lubrimanager", SECRET, ttl, Duration.ofDays(7)), clock);
    }

    private User user() {
        Branch branch = mock(Branch.class);
        when(branch.getId()).thenReturn(2L);
        User u = mock(User.class);
        when(u.getId()).thenReturn(42L);
        when(u.getUsername()).thenReturn("alice");
        when(u.getRole()).thenReturn(Role.MANAGER);
        when(u.getBranch()).thenReturn(branch);
        return u;
    }

    @Test
    void generatesAndParsesRoundTrip() {
        JwtService service = serviceWithTtl(Duration.ofMinutes(15));

        JwtService.AccessClaims claims = service.parse(service.generateAccessToken(user()));

        assertThat(claims.username()).isEqualTo("alice");
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo(Role.MANAGER);
        assertThat(claims.branchId()).isEqualTo(2L);
        assertThat(claims.jti()).isNotBlank();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = serviceWithTtl(Duration.ofSeconds(-10)); // déjà expiré à l'émission
        String token = service.generateAccessToken(user());

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = serviceWithTtl(Duration.ofMinutes(15));
        String token = service.generateAccessToken(user());
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = new JwtService(
                new JwtProperties("lubrimanager", "another-secret-another-secret-0123456789xx",
                        Duration.ofMinutes(15), Duration.ofDays(7)), clock)
                .generateAccessToken(user());

        assertThatThrownBy(() -> serviceWithTtl(Duration.ofMinutes(15)).parse(foreign))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }
}
