package com.lubrimanager.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardAccountIT extends AbstractIntegrationTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardService cardService;

    private long createCustomer(String token) throws Exception {
        String json = mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Client Carte","type":"PARTICULIER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private JsonNode createCard(String token, String body) throws Exception {
        String json = mockMvc.perform(post("/api/cards")
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
    void creatingCustomerOpensAccountWithZeroBalance() throws Exception {
        String token = adminToken();
        long customerId = createCustomer(token);

        mockMvc.perform(get("/api/customers/{id}/account", customerId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("XOF"))
                .andExpect(jsonPath("$.status").value("ACTIF"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void issuesCardWithGeneratedNumberInPendingState() throws Exception {
        String token = adminToken();
        long customerId = createCustomer(token);

        JsonNode card = createCard(token, """
                {"customerId":%d}
                """.formatted(customerId));

        assertThat(card.get("cardNo").asText()).matches("CARD-\\d{6}");
        assertThat(card.get("status").asText()).isEqualTo("EN_ATTENTE");
        assertThat(card.get("balance").asInt()).isZero();
        assertThat(card.hasNonNull("activatedAt")).isFalse(); // champ absent tant que la carte n'est pas activée
    }

    @Test
    void cardLifecycleActivateSuspendBlockAndInvalidTransition() throws Exception {
        String token = adminToken();
        long id = createCard(token, "{\"customerId\":%d}".formatted(createCustomer(token))).get("id").asLong();

        mockMvc.perform(patchStatus(token, id, "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activatedAt").isNotEmpty());

        mockMvc.perform(patchStatus(token, id, "SUSPENDUE")).andExpect(status().isOk());
        mockMvc.perform(patchStatus(token, id, "BLOQUEE")).andExpect(status().isOk());

        // BLOQUEE -> SUSPENDUE n'est pas autorisé
        mockMvc.perform(patchStatus(token, id, "SUSPENDUE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void statusEndpointRejectsDeactivatedTarget() throws Exception {
        String token = adminToken();
        long id = createCard(token, "{\"customerId\":%d}".formatted(createCustomer(token))).get("id").asLong();

        mockMvc.perform(patchStatus(token, id, "DESACTIVEE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void deactivateIsReservedToManager() throws Exception {
        String adminToken = adminToken();
        long id = createCard(adminToken, "{\"customerId\":%d}".formatted(createCustomer(adminToken))).get("id").asLong();

        createUser("agent_card", "AGENT", "Agent!2026");
        mockMvc.perform(post("/api/cards/{id}/deactivate", id)
                        .header("Authorization", bearer(accessToken("agent_card", "Agent!2026")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        createUser("manager_card", "MANAGER", "Manager!2026");
        mockMvc.perform(post("/api/cards/{id}/deactivate", id)
                        .header("Authorization", bearer(accessToken("manager_card", "Manager!2026")))
                        .contentType("application/json").content("{\"reason\":\"carte détériorée\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESACTIVEE"));
    }

    @Test
    void duplicateExternalNumberIsRejected() throws Exception {
        String token = adminToken();
        long customerId = createCustomer(token);
        createCard(token, """
                {"customerId":%d,"externalNo":"BADGE-0001"}
                """.formatted(customerId));

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"customerId":%d,"externalNo":"badge-0001"}
                                """.formatted(customerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void expiredCardCannotBeActivatedAndBatchMarksItExpired() throws Exception {
        String token = adminToken();
        long id = createCard(token, "{\"customerId\":%d}".formatted(createCustomer(token))).get("id").asLong();

        Card card = cardRepository.findById(id).orElseThrow();
        card.setExpiresAt(LocalDate.now().minusDays(1));
        cardRepository.save(card);

        mockMvc.perform(patchStatus(token, id, "ACTIVE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));

        int updated = cardService.expireOverdueCards();
        assertThat(updated).isGreaterThanOrEqualTo(1);
        assertThat(cardRepository.findById(id).orElseThrow().getStatus()).isEqualTo(CardStatus.EXPIREE);
    }

    @Test
    void cardsAreListedUnderTheirCustomerAndSearchable() throws Exception {
        String token = adminToken();
        long customerId = createCustomer(token);
        String cardNo1 = createCard(token, "{\"customerId\":%d}".formatted(customerId)).get("cardNo").asText();
        createCard(token, "{\"customerId\":%d}".formatted(customerId));

        mockMvc.perform(get("/api/customers/{id}/cards", customerId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/cards").param("q", cardNo1).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].cardNo").value(cardNo1));

        mockMvc.perform(get("/api/cards").param("customerId", String.valueOf(customerId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void cannotIssueCardForArchivedCustomer() throws Exception {
        String token = adminToken();
        long customerId = createCustomer(token);
        mockMvc.perform(patch("/api/customers/{id}/status", customerId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"status\":\"ARCHIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"customerId\":%d}".formatted(customerId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    private static org.springframework.test.web.servlet.RequestBuilder patchStatus(
            String token, long id, String status) {
        return patch("/api/cards/{id}/status", id)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"status\":\"%s\"}".formatted(status));
    }
}
