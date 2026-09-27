package org.example.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class PiiCrypto implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:v1:";
    private static final String DEV_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final SecretKeySpec KEY = loadKey();

    @Override
    public String convertToDatabaseColumn(String value) {
        if (value == null || value.isBlank() || value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível proteger dado pessoal", exception);
        }
    }

    @Override
    public String convertToEntityAttribute(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Não foi possível ler dado pessoal protegido", exception);
        }
    }

    public static boolean isDefaultKey() {
        String configured = configuredKey();
        return configured == null || configured.isBlank() || DEV_KEY.equals(configured);
    }

    private static SecretKeySpec loadKey() {
        String configured = configuredKey();
        String encoded = configured == null || configured.isBlank() ? DEV_KEY : configured;
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY deve ser Base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY deve conter 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private static String configuredKey() {
        String property = System.getProperty("PII_ENCRYPTION_KEY");
        return property == null ? System.getenv("PII_ENCRYPTION_KEY") : property;
    }
}
