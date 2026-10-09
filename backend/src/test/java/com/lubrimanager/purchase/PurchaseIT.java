package com.lubrimanager.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.common.Actor;
import com.lubrimanager.purchase.dto.CreatePurchaseRequest;
import com.lubrimanager.purchase.dto.PurchaseLineRequest;
import com.lubrimanager.support.AbstractIntegrationTest;
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

class PurchaseIT extends AbstractIntegrationTest {

    @Autowired private PurchaseService purchaseService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;

    private long newCustomer(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"Achat\",\"type\":\"ENTREPRISE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long activeCardOf(String token, long customerId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/cards").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long accountOfCustomer(String token, long customerId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/customers/{id}/account", customerId)
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long product(String token, long price) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"Huile\",\"brand\":\"B\",\"unit\":\"Bidon\",\"unitPrice\":%d}".formatted(price)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private void recharge(String token, long customerId, long amount) throws Exception {
        mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"customerId\":%d,\"amount\":%d}".formatted(customerId, amount)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions postPurchase(String token, String key, String body)
            throws Exception {
        var req = post("/api/purchases").header("Authorization", bearer(token))
                .contentType("application/json").content(body);
        if (key != null) {
            req = req.header("Idempotency-Key", key);
        }
        return mockMvc.perform(req);
    }

    // ------------------------------------------------------------------

    @Test
    void debitsCardForProductsAndRecordsAnAchatTransaction() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long accountId = accountOfCustomer(token, customerId);
        long p1 = product(token, 8_500);
        long p2 = product(token, 3_000);
        recharge(token, customerId, 100_000);

        JsonNode r = objectMapper.readTree(postPurchase(token, UUID.randomUUID().toString(), """
                {"cardId":%d,"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":1}]}
                """.formatted(cardId, p1, p2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(20000))
                .andExpect(jsonPath("$.balanceBefore").value(100000))
                .andExpect(jsonPath("$.balanceAfter").value(80000))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        assertThat(r.get("reference").asText()).matches("ACHT-\\d{8}-\\d{6}");
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("80000");

        mockMvc.perform(get("/api/transactions").param("type", "ACHAT").param("accountId", String.valueOf(accountId))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(-20000));

        mockMvc.perform(get("/api/purchases").param("cardId", String.valueOf(cardId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(r.get("reference").asText()));
    }

    @Test
    void refusesWhenBalanceIsInsufficientAndWritesNothing() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long accountId = accountOfCustomer(token, customerId);
        long productId = product(token, 50_000);
        recharge(token, customerId, 30_000);

        postPurchase(token, UUID.randomUUID().toString(),
                "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(cardId, productId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("30000");
        mockMvc.perform(get("/api/transactions").param("type", "ACHAT").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/audit").param("action", "PURCHASE_REFUSED").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long productId = product(token, 1_000);
        recharge(token, customerId, 10_000);

        postPurchase(token, null,
                "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(cardId, productId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void concurrentPurchasesWithSameKeyDebitExactlyOnce() throws Exception {
        String token = adminToken();
        long customerId = newCustomer(token);
        long cardId = activeCardOf(token, customerId);
        long accountId = accountOfCustomer(token, customerId);
        long productId = product(token, 5_000);
        recharge(token, customerId, 100_000);

        var admin = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        Actor actor = new Actor(admin.getId(), admin.getUsername(), admin.getBranch().getId());
        var request = new CreatePurchaseRequest(cardId,
                List.of(new PurchaseLineRequest(productId, new BigDecimal("2"))), null);
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
                    return purchaseService.createPurchase(request, key, actor).reference();
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
                .as("le compte n'est débité qu'une fois").isEqualByComparingTo("90000");
    }
}
