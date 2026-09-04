package com.lubrimanager.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerControllerIT extends AbstractIntegrationTest {

    private JsonNode createCustomer(String token, String body) throws Exception {
        String json = mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String createUser(String username, String role, String password) throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s","fullName":"%s","role":"%s"}
                                """.formatted(username, password, username, role)))
                .andExpect(status().isCreated());
        return username;
    }

    @Test
    void createsCustomerWithGeneratedSequentialNumber() throws Exception {
        String token = adminToken();

        JsonNode first = createCustomer(token, """
                {"fullName":"ABC SARL","companyName":"ABC SARL","phone":"+22890000001",
                 "email":"contact@abc.tg","type":"ENTREPRISE"}
                """);
        JsonNode second = createCustomer(token, """
                {"fullName":"Kodjo Mensah","phone":"+22891111111","type":"PARTICULIER"}
                """);

        assertThat(first.get("customerNo").asText()).matches("CLI-\\d{6}");
        assertThat(first.get("status").asText()).isEqualTo("ACTIF");
        assertThat(first.get("branchId").asLong()).isPositive();

        long n1 = Long.parseLong(first.get("customerNo").asText().substring(4));
        long n2 = Long.parseLong(second.get("customerNo").asText().substring(4));
        assertThat(n2).isEqualTo(n1 + 1);
    }

    @Test
    void searchMatchesNumberNameAndPhone() throws Exception {
        String token = adminToken();
        JsonNode c = createCustomer(token, """
                {"fullName":"Recherche Cible","companyName":"CibleCorp","phone":"+22899887766","type":"ENTREPRISE"}
                """);
        String customerNo = c.get("customerNo").asText();

        for (String q : new String[] {customerNo, "cible", "99887766"}) {
            mockMvc.perform(get("/api/customers").param("q", q).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.customerNo=='" + customerNo + "')]").exists());
        }
    }

    @Test
    void auditorCanReadButNotCreate() throws Exception {
        createUser("auditeur1", "AUDITEUR", "Auditeur!2026");
        String auditorToken = accessToken("auditeur1", "Auditeur!2026");

        mockMvc.perform(get("/api/customers").header("Authorization", bearer(auditorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(auditorToken))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Interdit","type":"PARTICULIER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void agentCreatesButCannotChangeStatus() throws Exception {
        createUser("agent1", "AGENT", "Agent!2026");
        String agentToken = accessToken("agent1", "Agent!2026");

        JsonNode created = createCustomer(agentToken, """
                {"fullName":"Client Agent","type":"PARTICULIER"}
                """);
        long id = created.get("id").asLong();

        mockMvc.perform(patch("/api/customers/{id}/status", id)
                        .header("Authorization", bearer(agentToken))
                        .contentType("application/json")
                        .content("""
                                {"status":"BLOQUE","reason":"test"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerChangesStatusAndUpdateIsPersisted() throws Exception {
        createUser("manager1", "MANAGER", "Manager!2026");
        String managerToken = accessToken("manager1", "Manager!2026");

        long id = createCustomer(managerToken, """
                {"fullName":"Avant Modif","type":"PARTICULIER"}
                """).get("id").asLong();

        mockMvc.perform(patch("/api/customers/{id}/status", id)
                        .header("Authorization", bearer(managerToken))
                        .contentType("application/json")
                        .content("""
                                {"status":"SUSPENDU","reason":"impayé"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDU"));

        mockMvc.perform(get("/api/customers/{id}", id).header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDU"));
    }

    @Test
    void unknownCustomerReturns404() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", 999999).header("Authorization", bearer(adminToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void missingRequiredFieldsAreRejected() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"companyName":"Sans nom ni type"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
