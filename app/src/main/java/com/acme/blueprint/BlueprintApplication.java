package com.acme.blueprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Composition root. Every direct sub-package of {@code com.acme.blueprint} —
 * {@code customers}, {@code notifications}, {@code shared} — is an
 * application module in Spring Modulith's sense. This project contains no
 * business code and depends on the modules at runtime only.
 */
@SpringBootApplication
@Modulithic(systemName = "Modular Monolith Blueprint", sharedModules = "shared")
public class BlueprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlueprintApplication.class, args);
    }
}
