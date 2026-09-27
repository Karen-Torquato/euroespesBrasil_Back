package org.example.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiCrypto — proteção de PII")
class PiiCryptoTest {

    @AfterEach
    void limparChavePii() {
        System.clearProperty("PII_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("deve cifrar e decifrar mantendo o valor original")
    void cifraEDecifra() {
        PiiCrypto crypto = new PiiCrypto();
        String plaintext = "529.982.247-25";

        String encrypted = crypto.convertToDatabaseColumn(plaintext);

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(crypto.convertToEntityAttribute(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("não deve recifrar valor já protegido")
    void naoRecifraValorProtegido() {
        PiiCrypto crypto = new PiiCrypto();
        String encrypted = "enc:v1:dGVzdGU=";

        assertThat(crypto.convertToDatabaseColumn(encrypted)).isEqualTo(encrypted);
    }

    @Test
    @DisplayName("deve detectar chave default quando propriedade não existir")
    void detectaChaveDefaultSemPropriedade() {
        System.clearProperty("PII_ENCRYPTION_KEY");
        assertThat(PiiCrypto.isDefaultKey()).isTrue();
    }

    @Test
    @DisplayName("não deve detectar chave default quando propriedade customizada existir")
    void naoDetectaChaveDefaultComPropriedadeCustomizada() {
        System.setProperty("PII_ENCRYPTION_KEY", "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo0NTY3ODkwMTIz");
        assertThat(PiiCrypto.isDefaultKey()).isFalse();
    }
}
