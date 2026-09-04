package com.lubrimanager.user;

import com.lubrimanager.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractIntegrationTest {

    private String createAgent(String username, String password) throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s","fullName":"Agent Test","role":"AGENT"}
                                """.formatted(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.branchId").isNumber());
        return username;
    }

    @Test
    void agentCannotAdministerUsers() throws Exception {
        String username = "agent_" + System.nanoTime();
        createAgent(username, "Agent!Pwd2026");

        mockMvc.perform(get("/api/users").header("Authorization", bearer(accessToken(username, "Agent!Pwd2026"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void userChangesOwnPasswordThenLogsInWithIt() throws Exception {
        String username = "pwd_" + System.nanoTime();
        createAgent(username, "Initial!2026");
        String token = accessToken(username, "Initial!2026");

        mockMvc.perform(post("/api/users/me/password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"Initial!2026","newPassword":"Renouvele!2026"}
                                """))
                .andExpect(status().isNoContent());

        accessToken(username, "Renouvele!2026");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"Initial!2026"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = "dup_" + System.nanoTime();
        createAgent(username, "First!2026");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"Second!2026","fullName":"Doublon","role":"AGENT"}
                                """.formatted(username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void weakPasswordIsRejectedWithValidationError() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"weak_%d","password":"court","fullName":"Faible","role":"AGENT"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }
}
