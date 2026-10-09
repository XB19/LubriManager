package com.lubrimanager.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductIT extends AbstractIntegrationTest {

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
    void createsProductWithTheFiveFieldsAndAReference() throws Exception {
        JsonNode p = createProduct(adminToken(), """
                {"name":"Huile moteur 20W50","brand":"LubriPlus","unit":"Bidon 5L",
                 "unitPrice":8500,"description":"Minerale multigrade"}
                """);

        assertThat(p.get("reference").asText()).matches("PROD-\\d{6}");
        assertThat(p.get("name").asText()).isEqualTo("Huile moteur 20W50");
        assertThat(p.get("brand").asText()).isEqualTo("LubriPlus");
        assertThat(p.get("unit").asText()).isEqualTo("Bidon 5L");
        assertThat(p.get("unitPrice").asInt()).isEqualTo(8500);
        assertThat(p.get("description").asText()).isEqualTo("Minerale multigrade");
        assertThat(p.hasNonNull("vatRate")).isFalse();
        assertThat(p.hasNonNull("stockQuantity")).isFalse();
        assertThat(p.hasNonNull("status")).isFalse();
    }

    @Test
    void descriptionIsOptional() throws Exception {
        JsonNode p = createProduct(adminToken(), """
                {"name":"Graisse universelle","brand":"GrasFort","unit":"Pot","unitPrice":3000}
                """);
        assertThat(p.hasNonNull("description")).isFalse();
    }

    @Test
    void brandIsRequired() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", bearer(adminToken()))
                        .contentType("application/json").content("""
                                {"name":"Sans marque","unit":"L","unitPrice":1000}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFractionalPrice() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", bearer(adminToken()))
                        .contentType("application/json").content("""
                                {"name":"X","brand":"Y","unit":"L","unitPrice":1250.50}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void managerUpdatesProductAndSearchFindsIt() throws Exception {
        createUser("mgr_prod", "MANAGER", "Manager!2026");
        String manager = accessToken("mgr_prod", "Manager!2026");

        long id = createProduct(manager, """
                {"name":"Huile hydraulique HV46","brand":"HydroBase","unit":"Fût 200L","unitPrice":95000}
                """).get("id").asLong();

        mockMvc.perform(put("/api/products/{id}", id).header("Authorization", bearer(manager))
                        .contentType("application/json").content("""
                                {"name":"Huile hydraulique HV46","brand":"HydroMax","unit":"Fût 200L","unitPrice":99000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(99000))
                .andExpect(jsonPath("$.brand").value("HydroMax"));

        mockMvc.perform(get("/api/products").param("q", "hydraulique").header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")]").exists());
    }

    @Test
    void agentCanReadButNotCreate() throws Exception {
        createUser("agent_prod", "AGENT", "Agent!2026");
        String agent = accessToken("agent_prod", "Agent!2026");

        mockMvc.perform(get("/api/products").header("Authorization", bearer(agent)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/products").header("Authorization", bearer(agent))
                        .contentType("application/json").content("""
                                {"name":"Interdit","brand":"Z","unit":"L","unitPrice":1000}
                                """))
                .andExpect(status().isForbidden());
    }
}
