package org.example.config;

import org.example.security.PiiCrypto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductionSafetyValidator — validações de segurança em prod")
class ProductionSafetyValidatorTest {

    @AfterEach
    void limparChavePii() {
        System.clearProperty("PII_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("deve falhar em prod com JWT fraco")
    void deveFalharComJwtFraco() {
        ProductionSafetyValidator validator = novoValidatorProd();
        ReflectionTestUtils.setField(validator, "jwtSecret", "curto");
        ReflectionTestUtils.setField(validator, "corsOrigins", "https://app.seudominio.com");
        ReflectionTestUtils.setField(validator, "adminPassword", "SenhaForte!123");
        ReflectionTestUtils.setField(validator, "createDefaultAdmin", false);
        ReflectionTestUtils.setField(validator, "seedEnabled", false);
        ReflectionTestUtils.setField(validator, "requireSsl", true);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db:5432/app?sslmode=require");
        System.setProperty("PII_ENCRYPTION_KEY", base64Key());

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("deve falhar em prod com chave PII padrão")
    void deveFalharComChavePiiPadrao() {
        ProductionSafetyValidator validator = novoValidatorProd();
        ReflectionTestUtils.setField(validator, "jwtSecret", "X".repeat(80));
        ReflectionTestUtils.setField(validator, "corsOrigins", "https://app.seudominio.com");
        ReflectionTestUtils.setField(validator, "adminPassword", "SenhaForte!123");
        ReflectionTestUtils.setField(validator, "createDefaultAdmin", false);
        ReflectionTestUtils.setField(validator, "seedEnabled", false);
        ReflectionTestUtils.setField(validator, "requireSsl", true);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db:5432/app?sslmode=require");

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PII_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("deve falhar em prod sem sslmode=require")
    void deveFalharSemSslmodeRequire() {
        ProductionSafetyValidator validator = novoValidatorProd();
        ReflectionTestUtils.setField(validator, "jwtSecret", "X".repeat(80));
        ReflectionTestUtils.setField(validator, "corsOrigins", "https://app.seudominio.com");
        ReflectionTestUtils.setField(validator, "adminPassword", "SenhaForte!123");
        ReflectionTestUtils.setField(validator, "createDefaultAdmin", false);
        ReflectionTestUtils.setField(validator, "seedEnabled", false);
        ReflectionTestUtils.setField(validator, "requireSsl", true);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db:5432/app");
        System.setProperty("PII_ENCRYPTION_KEY", base64Key());

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sslmode=require");
    }

    @Test
    @DisplayName("deve falhar em prod com CORS inseguro")
    void deveFalharComCorsInseguro() {
        ProductionSafetyValidator validator = novoValidatorProd();
        ReflectionTestUtils.setField(validator, "jwtSecret", "X".repeat(80));
        ReflectionTestUtils.setField(validator, "corsOrigins", "http://localhost:4200");
        ReflectionTestUtils.setField(validator, "adminPassword", "SenhaForte!123");
        ReflectionTestUtils.setField(validator, "createDefaultAdmin", false);
        ReflectionTestUtils.setField(validator, "seedEnabled", false);
        ReflectionTestUtils.setField(validator, "requireSsl", true);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db:5432/app?sslmode=require");
        System.setProperty("PII_ENCRYPTION_KEY", base64Key());

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ORIGINS");
    }

    @Test
    @DisplayName("deve falhar em prod quando seed estiver habilitado")
    void deveFalharComSeedAtivo() {
        ProductionSafetyValidator validator = novoValidatorProd();
        ReflectionTestUtils.setField(validator, "jwtSecret", "X".repeat(80));
        ReflectionTestUtils.setField(validator, "corsOrigins", "https://app.seudominio.com");
        ReflectionTestUtils.setField(validator, "adminPassword", "SenhaForte!123");
        ReflectionTestUtils.setField(validator, "createDefaultAdmin", false);
        ReflectionTestUtils.setField(validator, "seedEnabled", true);
        ReflectionTestUtils.setField(validator, "requireSsl", true);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db:5432/app?sslmode=require");
        System.setProperty("PII_ENCRYPTION_KEY", base64Key());

        assertThatThrownBy(() -> validator.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SEED_ENABLED");
    }

    @Test
    @DisplayName("deve passar com configuração segura em prod")
    void devePassarComConfiguracaoSegura() {
        ProductionSafetyValidator validator = novoValidatorProd();
        ReflectionTestUtils.setField(validator, "jwtSecret", "X".repeat(80));
        ReflectionTestUtils.setField(validator, "corsOrigins", "https://app.seudominio.com,https://admin.seudominio.com");
        ReflectionTestUtils.setField(validator, "adminPassword", "SenhaForte!123");
        ReflectionTestUtils.setField(validator, "createDefaultAdmin", false);
        ReflectionTestUtils.setField(validator, "seedEnabled", false);
        ReflectionTestUtils.setField(validator, "requireSsl", true);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db:5432/app?sslmode=require");
        System.setProperty("PII_ENCRYPTION_KEY", base64Key());

        assertThatCode(() -> validator.run()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deve ignorar validações fora de prod")
    void deveIgnorarForaDeProd() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        environment.setActiveProfiles("dev");
        ProductionSafetyValidator validator = new ProductionSafetyValidator(environment);
        ReflectionTestUtils.setField(validator, "jwtSecret", "curto");
        ReflectionTestUtils.setField(validator, "corsOrigins", "*");
        ReflectionTestUtils.setField(validator, "adminPassword", "Admin@12345");
        ReflectionTestUtils.setField(validator, "createDefaultAdmin", true);
        ReflectionTestUtils.setField(validator, "seedEnabled", true);
        ReflectionTestUtils.setField(validator, "requireSsl", false);
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:h2:mem:test");

        assertThatCode(() -> validator.run()).doesNotThrowAnyException();
    }

    private ProductionSafetyValidator novoValidatorProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return new ProductionSafetyValidator(environment);
    }

    private String base64Key() {
        return "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";
    }
}
