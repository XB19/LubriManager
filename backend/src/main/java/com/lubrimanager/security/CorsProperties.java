package com.lubrimanager.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origines autorisées à appeler l'API (poste Tauri, serveur de dev Vite).
 */
@ConfigurationProperties("lubrimanager.security.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
