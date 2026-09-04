package com.lubrimanager.security;

import com.lubrimanager.common.error.ApiException;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valide le jeton d'accès porté par l'en-tête {@code Authorization: Bearer …} et
 * renseigne le {@code SecurityContext}. Une vérification légère en base garantit
 * qu'un compte désactivé est rejeté sans attendre l'expiration du jeton (RG-17).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = "jwt.error.code";

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.AccessClaims claims = jwtService.parse(token);
                users.findById(claims.userId())
                        .filter(u -> u.isActive() && u.getUsername().equalsIgnoreCase(claims.username()))
                        .ifPresentOrElse(user -> {
                            AppUserDetails principal = AppUserDetails.from(user);
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }, () -> request.setAttribute(ERROR_ATTRIBUTE, ErrorCode.TOKEN_INVALID));
            } catch (ApiException ex) {
                request.setAttribute(ERROR_ATTRIBUTE, ex.code());
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
