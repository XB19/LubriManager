package com.lubrimanager.common.error;

/**
 * Exception applicative portant un {@link ErrorCode}. Levée par la couche service ;
 * traduite en réponse HTTP normalisée par {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message != null ? message : code.defaultMessage());
        this.code = code;
    }

    public ApiException(ErrorCode code) {
        this(code, null);
    }

    public ErrorCode code() {
        return code;
    }

    // Fabriques lisibles ---------------------------------------------------

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException businessRule(String message) {
        return new ApiException(ErrorCode.BUSINESS_RULE, message);
    }

    public static ApiException authenticationFailed() {
        return new ApiException(ErrorCode.AUTHENTICATION_FAILED);
    }

    public static ApiException accountLocked() {
        return new ApiException(ErrorCode.ACCOUNT_LOCKED);
    }

    public static ApiException tokenInvalid(String message) {
        return new ApiException(ErrorCode.TOKEN_INVALID, message);
    }
}
