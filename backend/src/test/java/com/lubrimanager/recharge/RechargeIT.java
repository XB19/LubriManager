package com.lubrimanager.recharge;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.common.Actor;
import com.lubrimanager.payment.PaymentMethodRepository;
import com.lubrimanager.recharge.dto.CreateRechargeRequest;
import com.lubrimanager.support.AbstractIntegrationTest;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.user.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RechargeIT extends AbstractIntegrationTest {

    @Autowired private RechargeService rechargeService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private UserRepository userRepository;

    private long paymentMethodId() {
        return paymentMethodRepository.findByCodeIgnoreCase("ESPECES").orElseThrow().getId();
    }

    private long newActiveCard(String token) throws Exception {
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"fullName\":\"Client Recharge\",\"type\":\"PARTICULIER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        long cardId = objectMapper.readTree(mockMvc.perform(post("/api/cards")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        return cardId;
    }

    private long accountIdOfCard(long cardId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/cards/{id}", cardId)
                        .header("Authorization", bearer(adminToken())))
                .andReturn().getResponse().getContentAsString()).get("accountId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions postRecharge(String token, String key, String body)
            throws Exception {
        var req = post("/api/recharges").header("Authorization", bearer(token))
                .contentType("application/json").content(body);
        if (key != null) {
            req = req.header("Idempotency-Key", key);
        }
        return mockMvc.perform(req);
    }

    private String rechargeBody(long cardId, long amount) {
        return "{\"cardId\":%d,\"amount\":%d,\"paymentMethodId\":%d}".formatted(cardId, amount, paymentMethodId());
    }

    private String createUser(String username, String role, String password) throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\"}"
                                .formatted(username, password, username, role)))
                .andExpect(status().isCreated());
        return username;
    }

    // ------------------------------------------------------------------

    @Test
    void rechargeBelowThresholdIsValidatedImmediately() throws Exception {
        String token = adminToken();
        long cardId = newActiveCard(token);

        JsonNode r = objectMapper.readTree(postRecharge(token, UUID.randomUUID().toString(), rechargeBody(cardId, 50_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.balanceBefore").value(0))
                .andExpect(jsonPath("$.balanceAfter").value(50000))
                .andReturn().getResponse().getContentAsString());

        assertThat(r.get("reference").asText()).matches("RECH-\\d{8}-\\d{6}");

        long accountId = accountIdOfCard(cardId);
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("50000");
        assertThat(transactionRepository.sumByAccountAndStatus(accountId, TransactionStatus.VALIDEE))
                .isEqualByComparingTo("50000");
    }

    @Test
    void rechargeAboveThresholdWaitsForManagerValidation() throws Exception {
        String adminToken = adminToken();
        long cardId = newActiveCard(adminToken);
        long accountId = accountIdOfCard(cardId);

        long rechargeId = objectMapper.readTree(postRecharge(adminToken, UUID.randomUUID().toString(),
                rechargeBody(cardId, 200_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("0");

        createUser("agent_rech", "AGENT", "Agent!2026");
        mockMvc.perform(post("/api/recharges/{id}/validate", rechargeId)
                        .header("Authorization", bearer(accessToken("agent_rech", "Agent!2026"))))
                .andExpect(status().isForbidden());

        createUser("manager_rech", "MANAGER", "Manager!2026");
        mockMvc.perform(post("/api/recharges/{id}/validate", rechargeId)
                        .header("Authorization", bearer(accessToken("manager_rech", "Manager!2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.balanceAfter").value(200000));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("200000");
    }

    @Test
    void managerRejectsPendingRechargeAndItCannotBeValidatedLater() throws Exception {
        String adminToken = adminToken();
        long cardId = newActiveCard(adminToken);
        long accountId = accountIdOfCard(cardId);
        long rechargeId = objectMapper.readTree(postRecharge(adminToken, UUID.randomUUID().toString(),
                rechargeBody(cardId, 300_000)).andReturn().getResponse().getContentAsString()).get("id").asLong();

        createUser("mgr2", "MANAGER", "Manager!2026");
        String mgr = accessToken("mgr2", "Manager!2026");

        mockMvc.perform(post("/api/recharges/{id}/reject", rechargeId).header("Authorization", bearer(mgr))
                        .contentType("application/json").content("{\"reason\":\"justificatif manquant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUSEE"));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("0");

        mockMvc.perform(post("/api/recharges/{id}/validate", rechargeId).header("Authorization", bearer(mgr)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutDoubleCredit() throws Exception {
        String token = adminToken();
        long cardId = newActiveCard(token);
        long accountId = accountIdOfCard(cardId);
        String key = UUID.randomUUID().toString();
        String body = rechargeBody(cardId, 40_000);

        String ref1 = objectMapper.readTree(postRecharge(token, key, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("reference").asText();
        String ref2 = objectMapper.readTree(postRecharge(token, key, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        assertThat(ref2).isEqualTo(ref1);
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("40000");
    }

    @Test
    void sameKeyDifferentBodyIsConflict() throws Exception {
        String token = adminToken();
        long cardId = newActiveCard(token);
        String key = UUID.randomUUID().toString();

        postRecharge(token, key, rechargeBody(cardId, 40_000)).andExpect(status().isCreated());
        postRecharge(token, key, rechargeBody(cardId, 41_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String token = adminToken();
        long cardId = newActiveCard(token);
        postRecharge(token, null, rechargeBody(cardId, 10_000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rechargeIsRefusedOnInactiveCardOverLimitOrFractionalAmount() throws Exception {
        String token = adminToken();

        // carte non activée
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"fullName\":\"C\",\"type\":\"PARTICULIER\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long pendingCard = objectMapper.readTree(mockMvc.perform(post("/api/cards")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"customerId\":%d}".formatted(customerId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        postRecharge(token, UUID.randomUUID().toString(), rechargeBody(pendingCard, 10_000))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));

        long activeCard = newActiveCard(token);
        // au-dessus du plafond de test (1 000 000)
        postRecharge(token, UUID.randomUUID().toString(), rechargeBody(activeCard, 2_000_000))
                .andExpect(status().isUnprocessableEntity());
        // montant non entier
        postRecharge(token, UUID.randomUUID().toString(),
                "{\"cardId\":%d,\"amount\":100.50,\"paymentMethodId\":%d}".formatted(activeCard, paymentMethodId()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ledgerAndRechargeListEndpointsExposeTheRecharge() throws Exception {
        String token = adminToken();
        long cardId = newActiveCard(token);
        long accountId = accountIdOfCard(cardId);
        String ref = objectMapper.readTree(postRecharge(token, UUID.randomUUID().toString(), rechargeBody(cardId, 25_000))
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        mockMvc.perform(get("/api/transactions").param("type", "RECHARGE").param("accountId", String.valueOf(accountId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reference").value(ref))
                .andExpect(jsonPath("$.content[0].amount").value(25000));

        mockMvc.perform(get("/api/transactions").param("from", "2020-01-01").param("to", "2100-01-01")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/transactions/reference/{ref}", ref).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("RECHARGE"));

        mockMvc.perform(get("/api/recharges").param("cardId", String.valueOf(cardId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(ref))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("ESPECES"));
    }

    @Test
    void concurrentRechargesWithSameKeyCreditExactlyOnce() throws Exception {
        String token = adminToken();
        long cardId = newActiveCard(token);
        long accountId = accountIdOfCard(cardId);

        var admin = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        Actor actor = new Actor(admin.getId(), admin.getUsername(), admin.getBranch().getId());
        var request = new CreateRechargeRequest(cardId, new BigDecimal("30000"), paymentMethodId(), null);
        String key = UUID.randomUUID().toString();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return rechargeService.createRecharge(request, key, actor).reference();
                } catch (RuntimeException e) {
                    failures.incrementAndGet();
                    return null;
                }
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<String> refs = new ArrayList<>();
        for (Future<String> f : futures) {
            String r = f.get();
            if (r != null) {
                refs.add(r);
            }
        }

        assertThat(failures.get()).as("aucune requête ne doit échouer (rejeu attendu)").isZero();
        assertThat(refs).hasSize(threads);
        assertThat(refs.stream().distinct().toList()).as("une seule référence produite").hasSize(1);
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .as("le compte n'est crédité qu'une fois").isEqualByComparingTo("30000");
        assertThat(transactionRepository.sumByAccountAndStatus(accountId, TransactionStatus.VALIDEE))
                .isEqualByComparingTo("30000");
    }
}
