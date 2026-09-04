package com.lubrimanager.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductCategoryIT extends AbstractIntegrationTest {

    private long firstCategoryId(String token) throws Exception {
        JsonNode categories = objectMapper.readTree(mockMvc.perform(get("/api/categories")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return categories.get(0).get("id").asLong();
    }

    private JsonNode createProduct(String token, String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
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
    void seedCategoriesArePresent() throws Exception {
        mockMvc.perform(get("/api/categories").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.name=='Huile moteur')]").exists());
    }

    @Test
    void createsProductWithReferenceAndTaxBreakdown() throws Exception {
        String token = adminToken();
        long categoryId = firstCategoryId(token);

        JsonNode p = createProduct(token, """
                {"name":"Huile moteur 20W50","brand":"LubriPlus","categoryId":%d,
                 "unit":"Bidon 5L","unitPrice":8500,"vatRate":18.00,"alertThreshold":15}
                """.formatted(categoryId));

        assertThat(p.get("reference").asText()).matches("PROD-\\d{6}");
        assertThat(p.get("status").asText()).isEqualTo("ACTIF");
        assertThat(p.get("unitPrice").asInt()).isEqualTo(8500);
        assertThat(p.get("priceExclTax").asDouble()).isEqualTo(7203.39);
        assertThat(p.get("taxAmount").asDouble()).isEqualTo(1296.61);
        assertThat(p.get("stockQuantity").asInt()).isZero();
        assertThat(p.get("lowStock").asBoolean()).isTrue(); // stock 0 <= seuil 15
    }

    @Test
    void rejectsFractionalPrice() throws Exception {
        String token = adminToken();
        long categoryId = firstCategoryId(token);
        mockMvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType("application/json").content("""
                                {"name":"X","categoryId":%d,"unit":"L","unitPrice":1250.50}
                                """.formatted(categoryId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void defaultsVatRateToEighteenWhenOmitted() throws Exception {
        String token = adminToken();
        long categoryId = firstCategoryId(token);
        JsonNode p = createProduct(token, """
                {"name":"Graisse universelle","categoryId":%d,"unit":"Pot","unitPrice":3000}
                """.formatted(categoryId));
        assertThat(p.get("vatRate").asDouble()).isEqualTo(18.0);
    }

    @Test
    void agentCanReadButNotCreateOrModify() throws Exception {
        createUser("agent_prod", "AGENT", "Agent!2026");
        String agent = accessToken("agent_prod", "Agent!2026");

        mockMvc.perform(get("/api/products").header("Authorization", bearer(agent)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/categories").header("Authorization", bearer(agent)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/products").header("Authorization", bearer(agent))
                        .contentType("application/json").content("""
                                {"name":"Interdit","categoryId":1,"unit":"L","unitPrice":1000}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/categories").header("Authorization", bearer(agent))
                        .contentType("application/json").content("{\"name\":\"Interdit\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerUpdatesPriceAndStatusAndSearchByCategoryAndLowStock() throws Exception {
        createUser("mgr_prod", "MANAGER", "Manager!2026");
        String manager = accessToken("mgr_prod", "Manager!2026");
        long categoryId = firstCategoryId(manager);

        long id = createProduct(manager, """
                {"name":"Huile hydraulique HV46","categoryId":%d,"unit":"Fût 200L","unitPrice":95000,"alertThreshold":5}
                """.formatted(categoryId)).get("id").asLong();

        mockMvc.perform(put("/api/products/{id}", id).header("Authorization", bearer(manager))
                        .contentType("application/json").content("""
                                {"name":"Huile hydraulique HV46","brand":"HydroMax","categoryId":%d,
                                 "unit":"Fût 200L","unitPrice":99000,"vatRate":18.00,"alertThreshold":5}
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(99000))
                .andExpect(jsonPath("$.brand").value("HydroMax"));

        mockMvc.perform(patch("/api/products/{id}/status", id).header("Authorization", bearer(manager))
                        .contentType("application/json").content("{\"status\":\"INACTIF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIF"));

        mockMvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId))
                        .param("lowStock", "true").header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")]").exists());
    }

    @Test
    void categoryInUseCannotBeDeleted() throws Exception {
        String token = adminToken();
        JsonNode cat = objectMapper.readTree(mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"name\":\"Additifs\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long catId = cat.get("id").asLong();

        // libre -> supprimable
        // d'abord on l'utilise
        createProduct(token, """
                {"name":"Additif diesel","categoryId":%d,"unit":"Flacon","unitPrice":2500}
                """.formatted(catId));

        mockMvc.perform(delete("/api/categories/{id}", catId).header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void duplicateCategoryNameIsRejected() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/categories").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"name\":\"Huile moteur\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
