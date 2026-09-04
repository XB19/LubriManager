package com.lubrimanager.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Horloge injectable : toute la logique lit l'heure via ce bean, ce qui rend les
 * horodatages testables de façon déterministe. Les dates sont toujours en UTC ;
 * l'affichage local (Africa/Lomé) est du ressort du client.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
