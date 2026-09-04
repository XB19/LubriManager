package com.lubrimanager.common.error;

import com.lubrimanager.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduit toute exception en {@link ApiError}. Les 5xx sont journalisées avec leur
 * stacktrace ; le client ne reçoit qu'un message générique et l'identifiant de requête.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, WebRequest request) {
        ErrorCode code = ex.code();
        if (code.status().is5xxServerError()) {
            log.error("Erreur applicative {} sur {}", code, path(request), ex);
        } else {
            log.debug("Erreur métier {} : {}", code, ex.getMessage());
        }
        return build(code, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, "Corps de requête illisible ou mal formé.", request, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, WebRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, "En-tête requis manquant : " + ex.getHeaderName() + ".", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, WebRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, "Paramètre requis manquant : " + ex.getParameterName() + ".", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, "Valeur invalide pour le paramètre « " + ex.getName() + " ».", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Violation d'intégrité sur {} : {}", path(request), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT, "Un enregistrement en conflit existe déjà.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Erreur non gérée sur {}", path(request), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request, null);
    }

    private ApiError.FieldViolation toViolation(FieldError fe) {
        return new ApiError.FieldViolation(fe.getField(),
                fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "valeur invalide");
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, WebRequest request,
                                           List<ApiError.FieldViolation> violations) {
        ApiError body = ApiError.of(code, message, path(request), MDC.get(RequestIdFilter.MDC_KEY), violations);
        return ResponseEntity.status(code.status()).body(body);
    }

    private String path(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            HttpServletRequest http = swr.getRequest();
            return http.getMethod() + " " + http.getRequestURI();
        }
        return request.getDescription(false);
    }
}
