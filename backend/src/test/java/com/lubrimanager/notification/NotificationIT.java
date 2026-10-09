package com.lubrimanager.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIT extends AbstractIntegrationTest {

    private long newCustomer(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"N\",\"type\":\"PARTICULIER\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long recharge(String token, long customerId, long amount) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"customerId\":%d,\"amount\":%d}".formatted(customerId, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("transactionId").asLong();
    }

    private void reverse(String token, long txnId) throws Exception {
        mockMvc.perform(post("/api/transactions/{id}/reversal", txnId).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"reason\":\"correction\"}"))
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
    void reversalNotifiesStaff() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long txnId = recharge(token, customerId, 50_000);
        reverse(token, txnId);

        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("TRANSACTION_REVERSED"))
                .andExpect(jsonPath("$.content[0].severity").value("ALERTE"));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.unread", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void agentDoesNotSeeStaffNotifications() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        reverse(token, recharge(token, customerId, 50_000));

        createUser("agent_notif", "AGENT", "Agent!2026");
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(accessToken("agent_notif", "Agent!2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void markReadAndMarkAll() throws Exception {
        String token = adminToken();
        long c1 = newCustomer(token);
        long c2 = newCustomer(token);
        reverse(token, recharge(token, c1, 20_000));
        reverse(token, recharge(token, c2, 20_000));

        String body = mockMvc.perform(get("/api/notifications").param("unreadOnly", "true")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");
        long firstId = content.get(0).get("id").asLong();

        mockMvc.perform(patch("/api/notifications/{id}/read", firstId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.unread").value(0));
    }
}
