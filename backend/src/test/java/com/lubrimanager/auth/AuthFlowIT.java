package com.lubrimanager.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    void adminLogsInAndReachesProtectedEndpoint() throws Exception {
        JsonNode tokens = login(ADMIN, ADMIN_PWD);
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        assertThat(tokens.get("user").get("role").asText()).isEqualTo("ADMIN");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(tokens.get("accessToken").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void malformedTokenIsRejectedAsInvalid() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void refreshRotatesTokenAndInvalidatesTheOldOne() throws Exception {
        String oldRefresh = login(ADMIN, ADMIN_PWD).get("refreshToken").asText();

        var refreshed = objectMapper.readTree(mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(oldRefresh);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void accountLocksAfterThreeFailedAttempts() throws Exception {
        String username = "lock_" + System.nanoTime();
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"Correct!2026","fullName":"Verrou","role":"AGENT"}
                                """.formatted(username)))
                .andExpect(status().isCreated());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content("""
                                    {"username":"%s","password":"WRONG"}
                                    """.formatted(username)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"WRONG"}
                                """.formatted(username)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"Correct!2026"}
                                """.formatted(username)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }
}
