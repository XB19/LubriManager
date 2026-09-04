package com.lubrimanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lubrimanager.common.error.ApiError;
import com.lubrimanager.common.error.ErrorCode;
import com.lubrimanager.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Rendu JSON normalisé pour les accès non authentifiés (401). Le code d'erreur
 * précis est fourni par {@link JwtAuthenticationFilter} quand un jeton était présent.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attr = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE);
        ErrorCode code = attr instanceof ErrorCode ec ? ec : ErrorCode.AUTHENTICATION_FAILED;

        ApiError body = ApiError.of(code, code.defaultMessage(),
                request.getMethod() + " " + request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY));

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
