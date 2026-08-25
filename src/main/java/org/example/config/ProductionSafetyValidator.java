package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

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

        if (jwtSecret.contains("MudeMeParaUmSegredo")) {
            throw new IllegalStateException("Configuracao insegura: JWT_SECRET padrao detectado em profile prod");
        }

        if (corsOrigins.isBlank() || corsOrigins.contains("localhost")) {
            throw new IllegalStateException("Configuracao insegura: CORS_ORIGINS invalido para profile prod");
        }

        if (createDefaultAdmin && "Admin@12345".equals(adminPassword)) {
            throw new IllegalStateException("Configuracao insegura: senha admin padrao detectada em profile prod");
        }
    }
}

