package com.lubrimanager.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lubrimanager.user.AdminBootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base des tests d'intégration : contexte Spring complet contre un PostgreSQL réel
 * (profil {@code test} -> base {@code lubrimanager_test}). Chaque test démarre sur
 * un schéma vierge : {@code flyway.clean()} + {@code migrate()} + recréation du
 * compte administrateur initial.
 *
 * <p>Docker / Testcontainers pourront être réintroduits pour la CI (Phase 11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PWD = "Admin!Test2026";

    @Autowired
    private Flyway flyway;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void resetSchema() throws Exception {
        flyway.clean();
        flyway.migrate();
        adminBootstrap.run(null);
    }

    protected JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String accessToken(String username, String password) throws Exception {
        return login(username, password).get("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return accessToken(ADMIN, ADMIN_PWD);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
