package org.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Testa se o contexto Spring sobe completamente sem erros.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contexto Spring — smoke test")
class EuroespesBrasilApplicationTest {

    @Test
    @DisplayName("contexto Spring deve carregar sem erros")
    void contextLoads() {
        // Se chegar aqui, o contexto subiu com sucesso
    }
}

