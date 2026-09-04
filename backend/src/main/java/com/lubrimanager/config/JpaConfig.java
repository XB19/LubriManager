package com.lubrimanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active le remplissage automatique de {@code createdAt} / {@code updatedAt}
 * (voir {@link com.lubrimanager.common.domain.BaseEntity}).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
