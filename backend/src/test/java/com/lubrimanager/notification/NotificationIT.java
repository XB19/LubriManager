package com.lubrimanager.notification;

import com.lubrimanager.payment.PaymentMethodRepository;
import com.lubrimanager.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    private long especes() {
        return paymentMethodRepository.findByCodeIgnoreCase("ESPECES").orElseThrow().getId();
    }

    private long activeCard(String token) throws Exception {
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"N\",\"type\":\"PARTICULIER\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        return objectMapper.readTree(mockMvc.perform(post("/api/cards").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long product(String token, long threshold) throws Exception {
        long categoryId = objectMapper.readTree(mockMvc.perform(get("/api/categories").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        return objectMapper.readTree(mockMvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"P\",\"categoryId\":%d,\"unit\":\"U\",\"unitPrice\":1000,\"alertThreshold\":%d}"
                                .formatted(categoryId, threshold)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void movement(String token, long productId, String type, int qty, String reason) throws Exception {
        String body = reason == null
                ? "{\"type\":\"%s\",\"productId\":%d,\"quantity\":%d}".formatted(type, productId, qty)
                : "{\"type\":\"%s\",\"productId\":%d,\"quantity\":%d,\"reason\":\"%s\"}".formatted(type, productId, qty, reason);
        mockMvc.perform(post("/api/inventory/movements").header("Authorization", bearer(token))
                .contentType("application/json").content(body)).andExpect(status().isCreated());
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
    void pendingRechargeNotifiesStaff() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"cardId\":%d,\"amount\":200000,\"paymentMethodId\":%d}".formatted(cardId, especes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"));

        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("RECHARGE_PENDING"))
                .andExpect(jsonPath("$.content[0].severity").value("ALERTE"));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.unread", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void lowStockNotifiesOnlyWhenThresholdIsCrossed() throws Exception {
        String token = adminToken();
        long productId = product(token, 20);

        movement(token, productId, "ENTREE", 100, null);                 // 100 > 20 : pas de notif
        movement(token, productId, "AJUSTEMENT", -85, "casse");          // 15 <= 20 : franchissement -> notif
        movement(token, productId, "AJUSTEMENT", -5, "casse");           // 10 : toujours bas -> pas de nouvelle notif

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type=='STOCK_LOW')]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void agentDoesNotSeeStaffNotifications() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"cardId\":%d,\"amount\":200000,\"paymentMethodId\":%d}".formatted(cardId, especes())));

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
        long p1 = product(token, 5);
        movement(token, p1, "ENTREE", 100, null);
        movement(token, p1, "AJUSTEMENT", -97, "casse"); // 3 <= 5 : notif 1
        long p2 = product(token, 5);
        movement(token, p2, "ENTREE", 100, null);
        movement(token, p2, "AJUSTEMENT", -98, "casse"); // 2 <= 5 : notif 2

        String body = mockMvc.perform(get("/api/notifications").param("unreadOnly", "true")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(body).get("content").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/notifications/{id}/read", firstId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.unread").value(0));
    }
}
