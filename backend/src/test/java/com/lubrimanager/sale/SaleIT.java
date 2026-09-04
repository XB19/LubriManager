package com.lubrimanager.sale;

import com.fasterxml.jackson.databind.JsonNode;
import com.lubrimanager.account.AccountRepository;
import com.lubrimanager.payment.PaymentMethodRepository;
import com.lubrimanager.product.ProductRepository;
import com.lubrimanager.support.AbstractIntegrationTest;
import com.lubrimanager.transaction.TransactionRepository;
import com.lubrimanager.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SaleIT extends AbstractIntegrationTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;

    private long paymentMethodId() {
        return paymentMethodRepository.findByCodeIgnoreCase("ESPECES").orElseThrow().getId();
    }

    private long categoryId(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/categories").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
    }

    /** Crée un client + carte ACTIVE, renvoie l'id de la carte. */
    private long activeCard(String token) throws Exception {
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"fullName\":\"Client Vente\",\"type\":\"ENTREPRISE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        return objectMapper.readTree(mockMvc.perform(post("/api/cards")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long accountOfCard(String token, long cardId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/cards/{id}", cardId).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get("accountId").asLong();
    }

    /** Recharge réelle (par tranches sous le seuil de validation de test = 100 000). */
    private void topUp(String token, long cardId, long amount) throws Exception {
        long remaining = amount;
        while (remaining > 0) {
            long chunk = Math.min(remaining, 90_000);
            mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType("application/json")
                            .content("{\"cardId\":%d,\"amount\":%d,\"paymentMethodId\":%d}"
                                    .formatted(cardId, chunk, paymentMethodId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("VALIDEE"));
            remaining -= chunk;
        }
    }

    private long stockedProduct(String token, long price, long stockQty) throws Exception {
        long id = objectMapper.readTree(mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"name\":\"Huile 20W50\",\"categoryId\":%d,\"unit\":\"Bidon 5L\",\"unitPrice\":%d,\"alertThreshold\":5}"
                                .formatted(categoryId(token), price)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        if (stockQty > 0) {
            mockMvc.perform(post("/api/inventory/movements").header("Authorization", bearer(token))
                            .contentType("application/json")
                            .content("{\"type\":\"ENTREE\",\"productId\":%d,\"quantity\":%d}".formatted(id, stockQty)))
                    .andExpect(status().isCreated());
        }
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions postSale(String token, String key, String body)
            throws Exception {
        var req = post("/api/sales").header("Authorization", bearer(token))
                .contentType("application/json").content(body);
        if (key != null) {
            req = req.header("Idempotency-Key", key);
        }
        return mockMvc.perform(req);
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
    void saleDebitsAccountReducesStockAndFreezesPrices_example17() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long accountId = accountOfCard(token, cardId);
        long productId = stockedProduct(token, 8500, 250);
        topUp(token, cardId, 350_000);

        JsonNode sale = objectMapper.readTree(postSale(token, UUID.randomUUID().toString(), """
                {"cardId":%d,"items":[{"productId":%d,"quantity":10}]}
                """.formatted(cardId, productId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.total").value(85000))
                .andExpect(jsonPath("$.balanceBefore").value(350000))
                .andExpect(jsonPath("$.balanceAfter").value(265000))
                .andExpect(jsonPath("$.items[0].unitPrice").value(8500))
                .andExpect(jsonPath("$.items[0].lineTotal").value(85000))
                .andReturn().getResponse().getContentAsString());
        assertThat(sale.get("reference").asText()).matches("VENT-\\d{8}-\\d{6}");
        String reference = sale.get("reference").asText();

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("265000");
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualByComparingTo("240");

        mockMvc.perform(get("/api/transactions").param("type", "ACHAT").param("accountId", String.valueOf(accountId))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].amount").value(-85000))
                .andExpect(jsonPath("$.content[0].reference").value(reference));

        mockMvc.perform(get("/api/inventory/movements").param("type", "SORTIE").param("productId", String.valueOf(productId))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].quantity").value(-10))
                .andExpect(jsonPath("$.content[0].reference").value(reference));

        // invariant grand livre
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo(transactionRepository.sumByAccountAndStatus(accountId, TransactionStatus.VALIDEE));
    }

    @Test
    void insufficientBalanceRefusesSaleAndChangesNothing_example18() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long accountId = accountOfCard(token, cardId);
        long productId = stockedProduct(token, 30_000, 100);
        topUp(token, cardId, 20_000);

        postSale(token, UUID.randomUUID().toString(), """
                {"cardId":%d,"items":[{"productId":%d,"quantity":1}]}
                """.formatted(cardId, productId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Solde insuffisant")));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("20000");
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualByComparingTo("100");
        mockMvc.perform(get("/api/sales").param("customerId", "0").header("Authorization", bearer(token)));
        mockMvc.perform(get("/api/transactions").param("type", "ACHAT").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void insufficientStockRefusesSale() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long productId = stockedProduct(token, 1000, 5);
        topUp(token, cardId, 500_000);

        postSale(token, UUID.randomUUID().toString(), """
                {"cardId":%d,"items":[{"productId":%d,"quantity":10}]}
                """.formatted(cardId, productId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Stock insuffisant")));
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void saleRefusedOnSuspendedCardOrInactiveProduct() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long productId = stockedProduct(token, 1000, 50);
        topUp(token, cardId, 90_000);

        mockMvc.perform(patch("/api/cards/{id}/status", cardId).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"status\":\"SUSPENDUE\"}"))
                .andExpect(status().isOk());
        postSale(token, UUID.randomUUID().toString(),
                "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(cardId, productId))
                .andExpect(status().isUnprocessableEntity());

        long card2 = activeCard(token);
        topUp(token, card2, 90_000);
        mockMvc.perform(patch("/api/products/{id}/status", productId).header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"status\":\"INACTIF\"}"))
                .andExpect(status().isOk());
        postSale(token, UUID.randomUUID().toString(),
                "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(card2, productId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emptyCartAndMissingIdempotencyKeyAreRejected() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long productId = stockedProduct(token, 1000, 10);
        topUp(token, cardId, 90_000);

        postSale(token, UUID.randomUUID().toString(), "{\"cardId\":%d,\"items\":[]}".formatted(cardId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        postSale(token, null, "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(cardId, productId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutDoubleDebitOrDoubleOutflow() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long accountId = accountOfCard(token, cardId);
        long productId = stockedProduct(token, 5000, 100);
        topUp(token, cardId, 90_000);
        String key = UUID.randomUUID().toString();
        String body = "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":4}]}".formatted(cardId, productId);

        String ref1 = objectMapper.readTree(postSale(token, key, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("reference").asText();
        String ref2 = objectMapper.readTree(postSale(token, key, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        assertThat(ref2).isEqualTo(ref1);
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("70000");
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualByComparingTo("96");
    }

    @Test
    void multiLineSaleAggregatesTotalsAndReducesEveryStock() throws Exception {
        String token = adminToken();
        long cardId = activeCard(token);
        long p1 = stockedProduct(token, 8500, 100);
        long p2 = stockedProduct(token, 3000, 100);
        topUp(token, cardId, 90_000);

        JsonNode sale = objectMapper.readTree(postSale(token, UUID.randomUUID().toString(), """
                {"cardId":%d,"items":[{"productId":%d,"quantity":3},{"productId":%d,"quantity":2}]}
                """.formatted(cardId, p1, p2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(31500))
                .andExpect(jsonPath("$.total").value(31500))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        long saleId = sale.get("id").asLong();
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/sales/{id}", saleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode item : detail.get("items")) {
            long pid = item.get("productId").asLong();
            int expected = pid == p1 ? 25500 : 6000;
            assertThat(item.get("lineTotal").asInt()).isEqualTo(expected);
        }

        assertThat(productRepository.findById(p1).orElseThrow().getStockQuantity()).isEqualByComparingTo("97");
        assertThat(productRepository.findById(p2).orElseThrow().getStockQuantity()).isEqualByComparingTo("98");

        mockMvc.perform(get("/api/sales/reference/{ref}", sale.get("reference").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saleId));
    }

    @Test
    void agentCanSellButAuditorCannot() throws Exception {
        String admin = adminToken();
        long cardId = activeCard(admin);
        long productId = stockedProduct(admin, 1000, 50);
        topUp(admin, cardId, 90_000);

        createUser("agent_sale", "AGENT", "Agent!2026");
        postSale(accessToken("agent_sale", "Agent!2026"), UUID.randomUUID().toString(),
                "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":2}]}".formatted(cardId, productId))
                .andExpect(status().isCreated());

        createUser("auditeur_sale", "AUDITEUR", "Auditeur!2026");
        postSale(accessToken("auditeur_sale", "Auditeur!2026"), UUID.randomUUID().toString(),
                "{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(cardId, productId))
                .andExpect(status().isForbidden());
    }
}
