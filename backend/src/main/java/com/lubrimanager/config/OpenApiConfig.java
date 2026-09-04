package com.lubrimanager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI lubriManagerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API LubriManager")
                        .version("0.2.0")
                        .description("Gestion des cartes, comptes, lubrifiants et transactions — Phase 2 : authentification.")
                        .contact(new Contact().name("LubriManager")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Jeton d'accès obtenu via POST /api/auth/login")));
    }
}
