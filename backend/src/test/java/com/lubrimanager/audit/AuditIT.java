package com.lubrimanager.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditIT extends AbstractIntegrationTest {

    private long paymentMethodEspeces(String token) throws Exception {
        JsonNode methods = objectMapper.readTree(mockMvc.perform(get("/api/payment-methods")
                .header("Authorization", bearer(token))).andReturn().getResponse().getContentAsString());
        for (JsonNode m : methods) {
            if (m.get("code").asText().equals("ESPECES")) {
                return m.get("id").asLong();
            }
        }
        throw new IllegalStateException("ESPECES manquant");
    }

    private long activeCard(String token) throws Exception {
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"fullName\":\"C\",\"type\":\"PARTICULIER\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        return objectMapper.readTree(mockMvc.perform(post("/api/cards")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
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
    void loginSuccessAndFailureAreAudited() throws Exception {
        try {
            mockMvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"username\":\"admin\",\"password\":\"WRONG\"}"));
        } catch (Exception ignored) {
            // 401 attendu
        }
        String token = adminToken(); // connexion réussie

        mockMvc.perform(get("/api/audit").param("action", "USER_LOGIN_FAILURE").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/audit").param("action", "USER_LOGIN_SUCCESS").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].username").value("admin"))
                .andExpect(jsonPath("$.content[0].ip").isNotEmpty());
    }

    @Test
    void rechargeIsAudited() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        JsonNode r = objectMapper.readTree(mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"cardId\":%d,\"amount\":40000,\"paymentMethodId\":%d}"
                                .formatted(cardId, paymentMethodEspeces(token))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/audit").param("action", "RECHARGE_CREATED").param("entityType", "recharge")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(r.get("reference").asText()));
    }

    @Test
    void refusedSaleIsAuditedButLeavesNoLedgerEntry() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long categoryId = objectMapper.readTree(mockMvc.perform(get("/api/categories").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        long productId = objectMapper.readTree(mockMvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"Cher\",\"categoryId\":%d,\"unit\":\"U\",\"unitPrice\":50000}".formatted(categoryId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/inventory/movements").header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":10}".formatted(productId)));

        mockMvc.perform(post("/api/sales").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(cardId, productId)))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/audit").param("action", "SALE_REFUSED").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/transactions").param("type", "ACHAT").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void cardAndCustomerStatusChangesAreAudited() throws Exception {
        String token = adminToken();
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"Statut\",\"type\":\"PARTICULIER\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long cardId = objectMapper.readTree(mockMvc.perform(post("/api/cards").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/cards/{id}/status", cardId).header("Authorization", bearer(token))
                .contentType("application/json").content("{\"status\":\"SUSPENDUE\",\"reason\":\"contrôle\"}"));
        mockMvc.perform(patch("/api/customers/{id}/status", customerId).header("Authorization", bearer(token))
                .contentType("application/json").content("{\"status\":\"ARCHIVE\"}"));

        mockMvc.perform(get("/api/audit").param("action", "CARD_STATUS_CHANGED").param("entityId", String.valueOf(cardId))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].oldValue").value(org.hamcrest.Matchers.containsString("ACTIVE")))
                .andExpect(jsonPath("$.content[0].newValue").value(org.hamcrest.Matchers.containsString("SUSPENDUE")));
        mockMvc.perform(get("/api/audit").param("action", "CUSTOMER_STATUS_CHANGED")
                        .param("entityId", String.valueOf(customerId)).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void auditIsReadableByAuditorAndManagerButNotAgent() throws Exception {
        createUser("agent_aud", "AGENT", "Agent!2026");
        createUser("auditeur_aud", "AUDITEUR", "Auditeur!2026");
        createUser("manager_aud", "MANAGER", "Manager!2026");

        mockMvc.perform(get("/api/audit").header("Authorization", bearer(accessToken("agent_aud", "Agent!2026"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit").header("Authorization", bearer(accessToken("auditeur_aud", "Auditeur!2026"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit").header("Authorization", bearer(accessToken("manager_aud", "Manager!2026"))))
                .andExpect(status().isOk());
    }
}
