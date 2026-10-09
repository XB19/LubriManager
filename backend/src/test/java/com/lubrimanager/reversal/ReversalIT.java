package com.lubrimanager.reversal;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReversalIT extends AbstractIntegrationTest {

    @Autowired private AccountRepository accountRepository;

    private long newCustomer(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"C\",\"type\":\"ENTREPRISE\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long activeCardOf(String token, long customerId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/cards").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long accountOfCustomer(String token, long customerId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/customers/{id}/account", customerId)
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode recharge(String token, long customerId, long amount) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"customerId\":%d,\"amount\":%d}".formatted(customerId, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long product(String token, long price) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"P\",\"brand\":\"B\",\"unit\":\"U\",\"unitPrice\":%d}".formatted(price)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode purchase(String token, long cardId, long productId, int qty) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/purchases").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":%d}]}"
                                .formatted(cardId, productId, qty)))
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

    private org.springframework.test.web.servlet.ResultActions reverse(String token, long txnId, String reason)
            throws Exception {
        return mockMvc.perform(post("/api/transactions/{id}/reversal", txnId).header("Authorization", bearer(token))
                .contentType("application/json").content("{\"reason\":\"" + reason + "\"}"));
    }

    // ------------------------------------------------------------------

    @Test
    void reverseRechargeCreditsBackAndFlagsOriginalAnnulee() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long accountId = accountOfCustomer(token, customerId);
        long txnId = recharge(token, customerId, 50_000).get("transactionId").asLong();
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("50000");

        reverse(token, txnId, "erreur de saisie")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalReference").value(org.hamcrest.Matchers.matchesPattern("ANNL-\\d{8}-\\d{6}")))
                .andExpect(jsonPath("$.reversalAmount").value(-50000))
                .andExpect(jsonPath("$.originalStatus").value("ANNULEE"))
                .andExpect(jsonPath("$.balanceAfter").value(0));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("0");
        mockMvc.perform(get("/api/transactions").param("type", "ANNULATION").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/audit").param("action", "TRANSACTION_REVERSED").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void reversePurchaseRefundsBalanceOnly() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long accountId = accountOfCustomer(token, customerId);
        long productId = product(token, 8_500);
        recharge(token, customerId, 90_000);
        long purchaseTxnId = purchase(token, cardId, productId, 3).get("transactionId").asLong();

        var balanceAfterPurchase = accountRepository.findById(accountId).orElseThrow().getBalance();
        assertThat(balanceAfterPurchase).isEqualByComparingTo("64500");

        reverse(token, purchaseTxnId, "client a rendu les articles")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalStatus").value("ANNULEE"))
                .andExpect(jsonPath("$.reversalAmount").value(25500));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo(balanceAfterPurchase.add(new java.math.BigDecimal("25500")));
    }

    @Test
    void cannotReverseRechargeWhenFundsAlreadySpent() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long productId = product(token, 50_000);
        long rechargeTxnId = recharge(token, customerId, 50_000).get("transactionId").asLong();
        purchase(token, cardId, productId, 1); // solde ramené à 0

        reverse(token, rechargeTxnId, "trop tard")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void cannotReverseTwiceAndReasonIsRequired() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long txnId = recharge(token, customerId, 20_000).get("transactionId").asLong();

        mockMvc.perform(post("/api/transactions/{id}/reversal", txnId).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        reverse(token, txnId, "ok").andExpect(status().isCreated());
        reverse(token, txnId, "encore")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void reversalRequiresManagerRole() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long txnId = recharge(token, customerId, 10_000).get("transactionId").asLong();

        createUser("agent_rev", "AGENT", "Agent!2026");
        reverse(accessToken("agent_rev", "Agent!2026"), txnId, "x").andExpect(status().isForbidden());

        createUser("manager_rev", "MANAGER", "Manager!2026");
        reverse(accessToken("manager_rev", "Manager!2026"), txnId, "correction").andExpect(status().isCreated());
    }

    @Test
    void reverseUnknownTransactionReturns404() throws Exception {
        reverse(adminToken(), 999999, "x").andExpect(status().isNotFound());
    }
}
