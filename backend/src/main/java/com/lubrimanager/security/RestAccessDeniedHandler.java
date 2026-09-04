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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Rendu JSON normalisé pour un utilisateur authentifié mais non autorisé (403).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiError body = ApiError.of(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(),
                request.getMethod() + " " + request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY));

        response.setStatus(ErrorCode.ACCESS_DENIED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
