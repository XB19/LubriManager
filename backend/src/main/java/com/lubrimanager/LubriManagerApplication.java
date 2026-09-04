package com.lubrimanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Point d'entrée de l'API LubriManager.
 *
 * <p>Le backend est le cœur métier et l'unique source de vérité ; les clients
 * (desktop Tauri, mobile, web) ne font qu'appeler cette API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LubriManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LubriManagerApplication.class, args);
    }
}
