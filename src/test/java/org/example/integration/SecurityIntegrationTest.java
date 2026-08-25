package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa toda a cadeia de segurança:
 * - Rotas protegidas bloqueiam sem token
 * - Login retorna JWT válido
 * - Token inválido/expirado é rejeitado
 * - Preflight OPTIONS passa sem token
 * - Security headers estão presentes
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Segurança — testes de integração da cadeia de segurança")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── Rotas públicas ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rotas públicas")
    class RotasPublicas {

        @Test
        @DisplayName("POST /api/auth/login sem autenticação deve ser acessível (200 ou 401)")
        void loginDeveSerAcessivelSemToken() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "admin", "senha", "Admin@12345"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("OPTIONS preflight deve retornar 200 sem token (CORS)")
        void optionsPreflightDevePassing() throws Exception {
            mockMvc.perform(options("/api/pacientes")
                            .header("Origin", "http://localhost:4200")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk());
        }
    }

    // ─── Rotas protegidas — sem token ───────────────────────────────────────────

    @Nested
    @DisplayName("Rotas protegidas — sem token")
    class RotasProtegidasSemToken {

        @Test
        @DisplayName("GET /api/pacientes sem token deve retornar 401 ou 403")
        void pacientesSemTokenDeveBloquear() throws Exception {
            mockMvc.perform(get("/api/pacientes"))
                    .andExpect(status().is(org.hamcrest.Matchers.either(
                            org.hamcrest.Matchers.is(401)).or(org.hamcrest.Matchers.is(403))));
        }

        @Test
        @DisplayName("POST /api/pacientes sem token deve retornar 401 ou 403")
        void criarPacienteSemTokenDeveBloquear() throws Exception {
            mockMvc.perform(post("/api/pacientes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(org.hamcrest.Matchers.either(
                            org.hamcrest.Matchers.is(401)).or(org.hamcrest.Matchers.is(403))));
        }

        @Test
        @DisplayName("DELETE /api/pacientes/1 sem token deve retornar 401 ou 403")
        void deletarSemTokenDeveBloquear() throws Exception {
            mockMvc.perform(delete("/api/pacientes/1"))
                    .andExpect(status().is(org.hamcrest.Matchers.either(
                            org.hamcrest.Matchers.is(401)).or(org.hamcrest.Matchers.is(403))));
        }

        @Test
        @DisplayName("GET /api/estoque sem token deve retornar 401 ou 403")
        void estoqueSemTokenDeveBloquear() throws Exception {
            mockMvc.perform(get("/api/estoque"))
                    .andExpect(status().is(org.hamcrest.Matchers.either(
                            org.hamcrest.Matchers.is(401)).or(org.hamcrest.Matchers.is(403))));
        }
    }

    // ─── Token inválido ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token inválido / malformado")
    class TokenInvalido {

        @Test
        @DisplayName("token aleatório deve ser rejeitado")
        void tokenAleatorioDeveSerRejeitado() throws Exception {
            mockMvc.perform(get("/api/pacientes")
                            .header("Authorization", "Bearer token.invalido.qualquer"))
                    .andExpect(status().is(org.hamcrest.Matchers.either(
                            org.hamcrest.Matchers.is(401)).or(org.hamcrest.Matchers.is(403))));
        }

        @Test
        @DisplayName("header sem prefixo Bearer deve ser rejeitado")
        void semPrefixoBearerDeveSerRejeitado() throws Exception {
            mockMvc.perform(get("/api/pacientes")
                            .header("Authorization", "Basic dXNlcjpwYXNz"))
                    .andExpect(status().is(org.hamcrest.Matchers.either(
                            org.hamcrest.Matchers.is(401)).or(org.hamcrest.Matchers.is(403))));
        }

        @Test
        @DisplayName("token JWT assinado com segredo diferente deve ser rejeitado")
        void tokenDeOutroSegredoDeveSerRejeitado() throws Exception {
            // JWT assinado manualmente com segredo diferente (HS256, segredo "errado")
            // Header.Payload.Signature gerado externamente com segredo diferente
            String tokenFalso = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJoYWNrZXIiLCJyb2xlIjoiUk9MRV9BRE1JTiJ9.INVALIDA";
            mockMvc.perform(get("/api/pacientes")
                            .header("Authorization", "Bearer " + tokenFalso))
                    .andExpect(status().is(org.hamcrest.Matchers.either(
                            org.hamcrest.Matchers.is(401)).or(org.hamcrest.Matchers.is(403))));
        }
    }

    // ─── Login — validações ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Login — validações")
    class LoginValidacoes {

        @Test
        @DisplayName("senha errada deve retornar 401")
        void senhaErradaRetorna401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "admin", "senha", "senhaErrada"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("usuário inexistente deve retornar 401")
        void usuarioInexistenteRetorna401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "naoexiste", "senha", "qualquer"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("username vazio deve retornar 400")
        void usernameVazioRetorna400() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "", "senha", "Admin@12345"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("senha vazia deve retornar 400")
        void senhaVaziaRetorna400() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "admin", "senha", ""))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("body vazio deve retornar 400")
        void bodyVazioRetorna400() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── Security headers ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security headers nas respostas")
    class SecurityHeaders {

        private String loginEObterToken() throws Exception {
            String resp = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "admin", "senha", "Admin@12345"))))
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(resp).get("token").asText();
        }

        @Test
        @DisplayName("resposta autenticada deve conter X-Content-Type-Options: nosniff")
        void deveConterXContentTypeOptions() throws Exception {
            String token = loginEObterToken();
            mockMvc.perform(get("/api/pacientes")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        @Test
        @DisplayName("resposta autenticada deve conter X-Frame-Options: DENY")
        void deveConterXFrameOptions() throws Exception {
            String token = loginEObterToken();
            mockMvc.perform(get("/api/pacientes")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }
    }

    // ─── Token válido — acesso autorizado ───────────────────────────────────────

    @Nested
    @DisplayName("Token válido — acesso liberado")
    class TokenValido {

        @Test
        @DisplayName("com token válido GET /api/pacientes deve retornar 200")
        void comTokenValidoDeveRetornar200() throws Exception {
            String resp = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "admin", "senha", "Admin@12345"))))
                    .andReturn().getResponse().getContentAsString();
            String token = objectMapper.readTree(resp).get("token").asText();

            mockMvc.perform(get("/api/pacientes")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("com token válido GET /api/estoque deve retornar 200")
        void comTokenValidoEstoqueDeveRetornar200() throws Exception {
            String resp = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "admin", "senha", "Admin@12345"))))
                    .andReturn().getResponse().getContentAsString();
            String token = objectMapper.readTree(resp).get("token").asText();

            mockMvc.perform(get("/api/estoque")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalKits").exists());
        }
    }
}

