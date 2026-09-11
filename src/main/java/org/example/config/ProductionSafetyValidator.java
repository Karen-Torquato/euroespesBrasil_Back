package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ProductionSafetyValidator implements CommandLineRunner {

    private final Environment environment;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.cors.allowed-origins:}")
    private String corsOrigins;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.seed.create-default-admin:false}")
    private boolean createDefaultAdmin;

    @Value("${app.seed.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.security.require-ssl:false}")
    private boolean requireSsl;

    public ProductionSafetyValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        boolean prodActive = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);

        if (!prodActive) {
            return;
        }

        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 64
                || jwtSecret.contains("MudeMeParaUmSegredo")) {
            throw new IllegalStateException("Configuracao insegura: JWT_SECRET padrao detectado em profile prod");
        }

        if (!requireSsl) {
            throw new IllegalStateException("Configuracao insegura: HTTPS deve ser obrigatorio em profile prod");
        }

        List<String> origins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin ->
                !origin.startsWith("https://") || origin.contains("localhost") || origin.contains("*"))) {
            throw new IllegalStateException("Configuracao insegura: CORS_ORIGINS invalido para profile prod");
        }

        if (seedEnabled) {
            throw new IllegalStateException("Configuracao insegura: APP_SEED_ENABLED deve ser false em profile prod");
        }

        if (createDefaultAdmin && "Admin@12345".equals(adminPassword)) {
            throw new IllegalStateException("Configuracao insegura: senha admin padrao detectada em profile prod");
        }
    }
}

