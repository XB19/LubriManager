package com.lubrimanager.dashboard;

import com.lubrimanager.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIT extends AbstractIntegrationTest {

    private long newCustomer(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"Dash\",\"type\":\"ENTREPRISE\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long activeCardOf(String token, long customerId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/cards").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void recharge(String token, long customerId, long amount) throws Exception {
        mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"customerId\":%d,\"amount\":%d}".formatted(customerId, amount)))
                .andExpect(status().isCreated());
    }

    private long product(String token, long price) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"Huile\",\"brand\":\"B\",\"unit\":\"U\",\"unitPrice\":%d}".formatted(price)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void purchase(String token, long cardId, long productId, int qty) throws Exception {
        mockMvc.perform(post("/api/purchases").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":%d}]}"
                                .formatted(cardId, productId, qty)))
                .andExpect(status().isCreated());
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
    void overviewAggregatesCountsAndMonthTotals() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long productId = product(token, 8_500);
        recharge(token, customerId, 90_000);
        purchase(token, cardId, productId, 2); // total 17 000

        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.customers", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.counts.activeCards", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.month.rechargeAmount").value(90000))
                .andExpect(jsonPath("$.month.rechargeCount").value(1))
                .andExpect(jsonPath("$.month.salesAmount").value(17000))
                .andExpect(jsonPath("$.month.salesCount").value(1));
    }

    @Test
    void topProductsCustomersAndUserActivity() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long productId = product(token, 5_000);
        recharge(token, customerId, 90_000);
        purchase(token, cardId, productId, 4);

        mockMvc.perform(get("/api/dashboard/top-products").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(productId))
                .andExpect(jsonPath("$[0].quantitySold").value(4));
        mockMvc.perform(get("/api/dashboard/top-customers").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].purchaseTotal").value(20000));
        mockMvc.perform(get("/api/dashboard/user-activity").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].salesCount").value(1))
                .andExpect(jsonPath("$[0].rechargeCount").value(1));
    }

    @Test
    void agentSeesOwnDayButNotGlobalDashboard() throws Exception {
        String adminToken = adminToken();
        long customerId = newCustomer(adminToken);
        createUser("agent_dash", "AGENT", "Agent!2026");
        String agentToken = accessToken("agent_dash", "Agent!2026");
        recharge(agentToken, customerId, 30_000);

        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(agentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/me").header("Authorization", bearer(agentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operator").value("agent_dash"))
                .andExpect(jsonPath("$.rechargeCount").value(1))
                .andExpect(jsonPath("$.rechargeAmount").value(30000));
    }
}
