package com.lubrimanager.audit;

import com.lubrimanager.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountAdjustmentIT extends AbstractIntegrationTest {

    private long accountId(String token) throws Exception {
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"Ajust\",\"type\":\"PARTICULIER\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        return objectMapper.readTree(mockMvc.perform(get("/api/customers/{id}/account", customerId)
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String createUser(String username, String role, String password) throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\"}"
                                .formatted(username, password, username, role)))
                .andExpect(status().isCreated());
        return username;
    }

    @Test
    void adminAdjustsBalanceUpThenDownWithLedgerAndAudit() throws Exception {
        String token = adminToken();
        long acc = accountId(token);

        mockMvc.perform(post("/api/accounts/{id}/adjustment", acc).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"amount\":10000,\"reason\":\"regularisation caisse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10000));

        mockMvc.perform(post("/api/accounts/{id}/adjustment", acc).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"amount\":-4000,\"reason\":\"erreur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(6000));

        mockMvc.perform(get("/api/transactions").param("type", "AJUSTEMENT").param("accountId", String.valueOf(acc))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/audit").param("action", "ACCOUNT_ADJUSTED").param("entityId", String.valueOf(acc))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void adjustmentGuards() throws Exception {
        String token = adminToken();
        long acc = accountId(token);

        mockMvc.perform(post("/api/accounts/{id}/adjustment", acc).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"amount\":-1,\"reason\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity()); // solde négatif
        mockMvc.perform(post("/api/accounts/{id}/adjustment", acc).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"amount\":0,\"reason\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity()); // nul
        mockMvc.perform(post("/api/accounts/{id}/adjustment", acc).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"amount\":100}"))
                .andExpect(status().isBadRequest()); // motif manquant
    }

    @Test
    void adjustmentIsAdminOnly() throws Exception {
        String token = adminToken();
        long acc = accountId(token);
        createUser("manager_adj", "MANAGER", "Manager!2026");

        mockMvc.perform(post("/api/accounts/{id}/adjustment", acc)
                        .header("Authorization", bearer(accessToken("manager_adj", "Manager!2026")))
                        .contentType("application/json").content("{\"amount\":100,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
