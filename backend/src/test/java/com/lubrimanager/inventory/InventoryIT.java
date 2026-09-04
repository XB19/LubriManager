package com.lubrimanager.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.product.ProductRepository;
import com.lubrimanager.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;

    private long newProduct(String token) throws Exception {
        long categoryId = objectMapper.readTree(mockMvc.perform(get("/api/categories")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        return objectMapper.readTree(mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("""
                                {"name":"Huile moteur 20W50","categoryId":%d,"unit":"Bidon 5L",
                                 "unitPrice":8500,"alertThreshold":20}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode move(String token, String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/inventory/movements")
                        .header("Authorization", bearer(token)).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
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
    void entryAdjustmentAndInventoryKeepStockConsistent() throws Exception {
        String token = adminToken();
        long productId = newProduct(token);

        JsonNode e1 = move(token, "{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":250,\"reference\":\"BL-001\"}".formatted(productId));
        assertThat(e1.get("stockBefore").asInt()).isZero();
        assertThat(e1.get("stockAfter").asInt()).isEqualTo(250);
        assertThat(e1.get("quantity").asInt()).isEqualTo(250);

        move(token, "{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":50}".formatted(productId));
        move(token, "{\"type\":\"AJUSTEMENT\",\"productId\":%d,\"quantity\":-10,\"reason\":\"casse\"}".formatted(productId));

        JsonNode inv = move(token, "{\"type\":\"INVENTAIRE\",\"productId\":%d,\"quantity\":285,\"reason\":\"comptage mensuel\"}".formatted(productId));
        assertThat(inv.get("quantity").asInt()).isEqualTo(-5); // 285 compté vs 290 attendu
        assertThat(inv.get("stockAfter").asInt()).isEqualTo(285);

        mockMvc.perform(get("/api/products/{id}", productId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.stockQuantity").value(285));

        // invariant : stock = somme des deltas
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualByComparingTo(movementRepository.sumQuantityByProduct(productId));
    }

    @Test
    void manualOutflowIsRejected() throws Exception {
        String token = adminToken();
        long productId = newProduct(token);
        mockMvc.perform(post("/api/inventory/movements").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"type\":\"SORTIE\",\"productId\":%d,\"quantity\":5}".formatted(productId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void movementCannotDriveStockNegative() throws Exception {
        String token = adminToken();
        long productId = newProduct(token);
        move(token, "{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":30}".formatted(productId));
        mockMvc.perform(post("/api/inventory/movements").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"type\":\"AJUSTEMENT\",\"productId\":%d,\"quantity\":-50,\"reason\":\"x\"}".formatted(productId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void adjustmentAndInventoryRequireAReason() throws Exception {
        String token = adminToken();
        long productId = newProduct(token);
        mockMvc.perform(post("/api/inventory/movements").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"type\":\"AJUSTEMENT\",\"productId\":%d,\"quantity\":5}".formatted(productId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void agentCanReadStockButNotMove() throws Exception {
        String token = adminToken();
        long productId = newProduct(token);
        move(token, "{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":10}".formatted(productId));

        createUser("agent_stk", "AGENT", "Agent!2026");
        String agent = accessToken("agent_stk", "Agent!2026");

        mockMvc.perform(get("/api/inventory/movements").param("productId", String.valueOf(productId))
                        .header("Authorization", bearer(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/inventory/movements").header("Authorization", bearer(agent))
                        .contentType("application/json")
                        .content("{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":5}".formatted(productId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lowStockEndpointListsProductsAtOrBelowThreshold() throws Exception {
        String token = adminToken();
        long productId = newProduct(token); // seuil 20, stock 0 -> bas

        mockMvc.perform(get("/api/inventory/low-stock").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + productId + ")]").exists());

        move(token, "{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":100}".formatted(productId)); // au-dessus du seuil

        mockMvc.perform(get("/api/inventory/low-stock").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + productId + ")]").doesNotExist());
    }

    @Test
    void productMovementHistoryIsReturnedNewestFirst() throws Exception {
        String token = adminToken();
        long productId = newProduct(token);
        move(token, "{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":40}".formatted(productId));
        move(token, "{\"type\":\"AJUSTEMENT\",\"productId\":%d,\"quantity\":-3,\"reason\":\"casse\"}".formatted(productId));

        mockMvc.perform(get("/api/products/{id}/movements", productId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("AJUSTEMENT"))
                .andExpect(jsonPath("$[1].type").value("ENTREE"));
    }
}
