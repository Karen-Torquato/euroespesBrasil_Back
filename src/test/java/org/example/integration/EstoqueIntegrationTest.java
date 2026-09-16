package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração completos para Estoque:
 * ENTRADA, SAÍDA, histórico, regras de negócio, segurança.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("Estoque — testes de integração completos")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EstoqueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;

    @BeforeAll
    static void obterToken(@Autowired MockMvc mockMvc,
                           @Autowired ObjectMapper objectMapper) throws Exception {
        String resp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin", "senha", "Admin@12345"))))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).get("token").asText();
    }

    // ─── Segurança ───────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/estoque sem token deve ser bloqueado")
    void estoqueSemTokenDeveBloquear() throws Exception {
        mockMvc.perform(get("/api/estoque"))
                .andExpect(status().is(either(is(401)).or(is(403))));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/estoque/movimentar sem token deve ser bloqueado")
    void movimentarSemTokenDeveBloquear() throws Exception {
        mockMvc.perform(post("/api/estoque/movimentar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tipo", "ENTRADA", "quantidade", 5))))
                .andExpect(status().is(either(is(401)).or(is(403))));
    }

    // ─── GET estoque ─────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("GET /api/estoque com token deve retornar totalKits e historico")
    void getEstoqueDeveRetornarEstrutura() throws Exception {
        mockMvc.perform(get("/api/estoque")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalKits").exists())
                .andExpect(jsonPath("$.historico").isArray());
    }

    // ─── ENTRADA ─────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("POST ENTRADA válida deve aumentar totalKits")
    void entradaDeveAumentarEstoque() throws Exception {
        // Captura valor atual
        String antes = mockMvc.perform(get("/api/estoque")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        int totalAntes = objectMapper.readTree(antes).get("totalKits").asInt();

        // Registra entrada de 10
        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "ENTRADA",
                                "quantidade", 10,
                                "motivo", "Reposição teste"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalKits").value(totalAntes + 10));
    }

    @Test
    @Order(5)
    @DisplayName("POST ENTRADA deve registrar no histórico")
    void entradaDeveRegistrarNoHistorico() throws Exception {
        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "ENTRADA",
                                "quantidade", 5,
                                "motivo", "Teste histórico"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historico[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.historico[0].quantidade").value(5));
    }

    // ─── SAÍDA ───────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("POST SAIDA válida deve reduzir totalKits")
    void saidaDeveReduzirEstoque() throws Exception {
        String antes = mockMvc.perform(get("/api/estoque")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        int totalAntes = objectMapper.readTree(antes).get("totalKits").asInt();

        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "SAIDA",
                                "quantidade", 3,
                                "motivo", "Retirada teste"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalKits").value(totalAntes - 3));
    }

    @Test
    @Order(7)
    @DisplayName("POST SAIDA maior que estoque deve retornar 409")
    void saidaMaiorQueEstoqueRetorna409() throws Exception {
        // Obtém total atual para garantir que a saída seja maior
        String antes = mockMvc.perform(get("/api/estoque")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        int total = objectMapper.readTree(antes).get("totalKits").asInt();

        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "SAIDA",
                                "quantidade", total + 1000,
                                "motivo", "Tentativa de saída inválida"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("insuficiente")));
    }

    // ─── Validações de entrada ───────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("POST sem tipo deve retornar 400")
    void semTipoRetorna400() throws Exception {
        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantidade", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(9)
    @DisplayName("POST com quantidade zero deve retornar 400")
    void quantidadeZeroRetorna400() throws Exception {
        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tipo", "ENTRADA", "quantidade", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    @DisplayName("POST com tipo inválido deve retornar 400")
    void tipoInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tipo", "INVALIDO", "quantidade", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ENTRADA")));
    }

    @Test
    @Order(11)
    @DisplayName("POST ENTRADA com tipo em minúsculo deve ser aceito (normalização)")
    void tipoMinusculoDeveSerNormalizado() throws Exception {
        mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "entrada",
                                "quantidade", 1,
                                "motivo", "normalização"))))
                .andExpect(status().isOk());
    }

    // ─── Resposta sem stack trace ────────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("Erro não deve expor stack trace no body")
    void erroNaoExpoStackTrace() throws Exception {
        String resp = mockMvc.perform(post("/api/estoque/movimentar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tipo", "INVALIDO", "quantidade", 5))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(resp).doesNotContain("at org.");
        assertThat(resp).doesNotContain("stackTrace");
        assertThat(resp).contains("message");
    }
}

