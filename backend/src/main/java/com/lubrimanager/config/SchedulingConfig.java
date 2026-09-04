package com.lubrimanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (expiration des cartes, etc.). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
