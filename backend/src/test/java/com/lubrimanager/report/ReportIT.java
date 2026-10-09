package com.lubrimanager.report;

import com.lubrimanager.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIT extends AbstractIntegrationTest {

    private String makePurchase() throws Exception {
        String token = adminToken();
        long customerId = objectMapper.readTree(mockMvc.perform(post("/api/customers").header("Authorization", bearer(token))
                        .contentType("application/json").content("{\"fullName\":\"Rap\",\"type\":\"ENTREPRISE\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long cardId = objectMapper.readTree(mockMvc.perform(post("/api/cards").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"customerId\":%d,\"activateImmediately\":true}".formatted(customerId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long productId = objectMapper.readTree(mockMvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"P\",\"brand\":\"B\",\"unit\":\"U\",\"unitPrice\":8500}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/recharges").header("Authorization", bearer(token))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"customerId\":%d,\"amount\":90000}".formatted(customerId)));
        return objectMapper.readTree(mockMvc.perform(post("/api/purchases").header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"cardId\":%d,\"items\":[{\"productId\":%d,\"quantity\":2}]}".formatted(cardId, productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("reference").asText();
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
    void purchasesReportAsJson() throws Exception {
        String reference = makePurchase();
        mockMvc.perform(get("/api/reports/purchases").param("format", "json").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rapport des débits carte"))
                .andExpect(jsonPath("$.columns[0]").value("Référence"))
                .andExpect(jsonPath("$.rows[0][0]").value(reference));
    }

    @Test
    void purchasesReportAsCsvIsDownloadable() throws Exception {
        String reference = makePurchase();
        byte[] body = mockMvc.perform(get("/api/reports/purchases").param("format", "csv")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body[0] & 0xFF).isEqualTo(0xEF); // BOM UTF-8
        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Référence;Statut;N° client").contains(reference);
    }

    @Test
    void rechargesAndAuditReports() throws Exception {
        makePurchase();
        mockMvc.perform(get("/api/reports/recharges").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/reports/audit").param("format", "csv").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());
    }

    @Test
    void reportsRequireElevatedRoleAndValidType() throws Exception {
        createUser("agent_rep", "AGENT", "Agent!2026");
        mockMvc.perform(get("/api/reports/purchases")
                        .header("Authorization", bearer(accessToken("agent_rep", "Agent!2026"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/nonsense").header("Authorization", bearer(adminToken())))
                .andExpect(status().isBadRequest());
    }
}
