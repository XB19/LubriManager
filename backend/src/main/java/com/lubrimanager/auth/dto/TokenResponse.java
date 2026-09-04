package com.lubrimanager.auth.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        MeResponse user) {

    public static TokenResponse bearer(String accessToken, long expiresIn, String refreshToken, MeResponse user) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, refreshToken, user);
    }
}
