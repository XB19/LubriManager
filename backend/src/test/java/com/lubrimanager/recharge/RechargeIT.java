package com.lubrimanager.recharge;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.common.Actor;
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
    @Autowired private UserRepository userRepository;

    private long newCustomer(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"fullName\":\"Client Recharge\",\"type\":\"PARTICULIER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long accountIdOfCustomer(long customerId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/customers/{id}/account", customerId)
                        .header("Authorization", bearer(adminToken())))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
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

    private String rechargeBody(long customerId, long amount) {
        return "{\"customerId\":%d,\"amount\":%d}".formatted(customerId, amount);
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
    void rechargeIsValidatedImmediately() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);

        JsonNode r = objectMapper.readTree(postRecharge(token, UUID.randomUUID().toString(),
                rechargeBody(customerId, 200_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.balanceBefore").value(0))
                .andExpect(jsonPath("$.balanceAfter").value(200000))
                .andReturn().getResponse().getContentAsString());

        assertThat(r.get("reference").asText()).matches("RECH-\\d{8}-\\d{6}");

        long accountId = accountIdOfCustomer(customerId);
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("200000");
        assertThat(transactionRepository.sumByAccountAndStatus(accountId, TransactionStatus.VALIDEE))
                .isEqualByComparingTo("200000");
    }

    @Test
    void agentCanRecharge() throws Exception {
        createUser("agent_rech", "AGENT", "Agent!2026");
        String agent = accessToken("agent_rech", "Agent!2026");
        long customerId = newCustomer(adminToken());

        postRecharge(agent, UUID.randomUUID().toString(), rechargeBody(customerId, 15_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VALIDEE"));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutDoubleCredit() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long accountId = accountIdOfCustomer(customerId);
        String key = UUID.randomUUID().toString();
        String body = rechargeBody(customerId, 40_000);

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
        long customerId = newCustomer(token);
        String key = UUID.randomUUID().toString();

        postRecharge(token, key, rechargeBody(customerId, 40_000)).andExpect(status().isCreated());
        postRecharge(token, key, rechargeBody(customerId, 41_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        postRecharge(token, null, rechargeBody(customerId, 10_000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rechargeIsRefusedForInactiveCustomerOrFractionalAmount() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);

        // montant non entier
        postRecharge(token, UUID.randomUUID().toString(),
                "{\"customerId\":%d,\"amount\":100.50}".formatted(customerId))
                .andExpect(status().isUnprocessableEntity());

        // client suspendu
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/customers/{id}/status", customerId).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"status\":\"SUSPENDU\"}"))
                .andExpect(status().isOk());
        postRecharge(token, UUID.randomUUID().toString(), rechargeBody(customerId, 10_000))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    @Test
    void ledgerAndRechargeListEndpointsExposeTheRecharge() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long accountId = accountIdOfCustomer(customerId);
        String ref = objectMapper.readTree(postRecharge(token, UUID.randomUUID().toString(),
                rechargeBody(customerId, 25_000))
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        mockMvc.perform(get("/api/transactions").param("type", "RECHARGE").param("accountId", String.valueOf(accountId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reference").value(ref))
                .andExpect(jsonPath("$.content[0].amount").value(25000));

        mockMvc.perform(get("/api/recharges").param("customerId", String.valueOf(customerId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(ref));
    }

    @Test
    void concurrentRechargesWithSameKeyCreditExactlyOnce() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long accountId = accountIdOfCustomer(customerId);

        var admin = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        Actor actor = new Actor(admin.getId(), admin.getUsername(), admin.getBranch().getId());
        var request = new CreateRechargeRequest(customerId, new BigDecimal("30000"), null);
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
    }
}
