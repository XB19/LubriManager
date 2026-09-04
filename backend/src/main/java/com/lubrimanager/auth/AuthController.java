package com.lubrimanager.auth;

import com.lubrimanager.auth.dto.LoginRequest;
import com.lubrimanager.auth.dto.LogoutRequest;
import com.lubrimanager.auth.dto.MeResponse;
import com.lubrimanager.auth.dto.RefreshRequest;
import com.lubrimanager.auth.dto.TokenResponse;
import com.lubrimanager.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentification")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Connexion : renvoie un jeton d'accès et un refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.username(), request.password(), userAgent(http), clientIp(http));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotation : échange un refresh token valide contre de nouveaux jetons")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), userAgent(http), clientIp(http));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Déconnexion : révoque le refresh token fourni")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public MeResponse me(@AuthenticationPrincipal AppUserDetails current) {
        return authService.me(current.getId());
    }

    private String userAgent(HttpServletRequest http) {
        return http.getHeader("User-Agent");
    }

    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
