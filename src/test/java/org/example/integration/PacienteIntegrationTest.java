package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração completos para Pacientes:
 * CRUD, validações, CPF mascarado, upload de anexo, regras de fluxo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("Paciente — testes de integração completos")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PacienteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static Long idPacienteAtivo;
    private static Long idPacienteRascunho;

    // ─── Setup: obter token ──────────────────────────────────────────────────────

    @BeforeAll
    static void obterToken(@Autowired MockMvc mockMvc,
                           @Autowired ObjectMapper objectMapper) throws Exception {
        String resp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin", "senha", "Admin@12345"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).get("token").asText();
    }

    // ─── Criação de rascunho ─────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST criar paciente rascunho deve retornar 201 com status Rascunho")
    void criarRascunho() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nome", "Paciente Rascunho Teste",
                "statusResultado", "Rascunho",
                "quantidadeKits", 1
        ));

        MvcResult result = mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusResultado").value("Rascunho"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        idPacienteRascunho = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ─── Criação de paciente ativo ────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("POST criar paciente ativo deve retornar 201 com status Pendente")
    void criarPacienteAtivo() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nome", "Paciente Ativo Teste",
                "cpf", "12345678901",
                "email", "ativo@teste.com",
                "telefone", "(11) 99999-0001",
                "endereco", "Rua Teste, 123",
                "quantidadeKits", 1
        ));

        MvcResult result = mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusResultado").value("Pendente"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        idPacienteAtivo = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ─── CPF mascarado ───────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("GET paciente por ID deve retornar CPF mascarado")
    void cpfDeveEstarMascaradoNaResposta() throws Exception {
        mockMvc.perform(get("/api/pacientes/" + idPacienteAtivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(not("12345678901"))) // não pode ser CPF puro
                .andExpect(jsonPath("$.cpf").value(containsString("***"))); // deve estar mascarado
    }

    @Test
    @Order(4)
    @DisplayName("GET todos os pacientes deve retornar CPF mascarado em todos")
    void listaPacientesCpfMascarado() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        // Nenhum CPF com 11 dígitos numéricos puros pode aparecer
        assertThat(json).doesNotContainPattern("\"cpf\":\"\\d{11}\"");
    }

    // ─── Validação de campos ─────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("POST sem nome deve retornar 400")
    void criarSemNomeRetorna400() throws Exception {
        mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadeKits\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(6)
    @DisplayName("POST com email inválido deve retornar 400")
    void criarComEmailInvalidoRetorna400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nome", "Teste Email",
                "email", "nao-e-um-email",
                "quantidadeKits", 1
        ));

        mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    @Order(7)
    @DisplayName("POST com nome maior que 200 chars deve retornar 400")
    void criarComNomeMuitoLongoRetorna400() throws Exception {
        String nomeGigante = "A".repeat(201);
        String body = objectMapper.writeValueAsString(Map.of(
                "nome", nomeGigante,
                "quantidadeKits", 1
        ));

        mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─── Atualização ────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("PUT atualizar nome deve retornar 200 com novo nome")
    void atualizarNomePaciente() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("nome", "Paciente Atualizado"));

        mockMvc.perform(put("/api/pacientes/" + idPacienteAtivo)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Paciente Atualizado"));
    }

    @Test
    @Order(9)
    @DisplayName("PUT paciente inexistente deve retornar 404")
    void atualizarPacienteInexistenteRetorna404() throws Exception {
        mockMvc.perform(put("/api/pacientes/999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nome", "X"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(10)
    @DisplayName("PUT paciente ativo não pode voltar para rascunho — deve retornar 409")
    void ativoNaoPodeVoltarParaRascunho() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nome", "Paciente Ativo Teste",
                "statusResultado", "Rascunho"
        ));

        mockMvc.perform(put("/api/pacientes/" + idPacienteAtivo)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("rascunho")));
    }

    // ─── Upload de anexo ────────────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("POST upload com extensão não permitida deve retornar 400")
    void uploadExtensaoNaoPermitida() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "virus.exe", "application/octet-stream", "conteudo".getBytes());

        mockMvc.perform(multipart("/api/pacientes/" + idPacienteAtivo + "/anexo")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("xtens")));
    }

    @Test
    @Order(12)
    @DisplayName("POST upload de arquivo vazio deve retornar 400")
    void uploadArquivoVazio() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "vazio.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/pacientes/" + idPacienteAtivo + "/anexo")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("POST upload de arquivo PDF válido deve retornar 200")
    void uploadArquivoPdfValido() throws Exception {
        byte[] pdfFake = "%PDF-1.4 conteudo de teste".getBytes();
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "exame.pdf", "application/pdf", pdfFake);

        mockMvc.perform(multipart("/api/pacientes/" + idPacienteAtivo + "/anexo")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idPacienteAtivo));
    }

    @Test
    @Order(14)
    @DisplayName("POST upload sem token deve retornar 401 ou 403")
    void uploadSemTokenDeveBloquear() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "exame.pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(multipart("/api/pacientes/" + idPacienteAtivo + "/anexo")
                        .file(arquivo))
                .andExpect(status().is(either(is(401)).or(is(403))));
    }

    // ─── Erro — body sem exposição de stack trace ────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("Erros não devem expor stack trace no body")
    void erroresNaoExpoempStackTrace() throws Exception {
        String resp = mockMvc.perform(get("/api/pacientes/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(resp).doesNotContain("at org.");
        assertThat(resp).doesNotContain("Exception");
        assertThat(resp).doesNotContain("stackTrace");
        assertThat(resp).contains("message");
    }

    // ─── Deleção ────────────────────────────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("DELETE paciente rascunho deve retornar 204")
    void deletarRascunhoRetorna204() throws Exception {
        mockMvc.perform(delete("/api/pacientes/" + idPacienteRascunho)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(17)
    @DisplayName("DELETE paciente já deletado deve retornar 404")
    void deletarPacienteJaDeletadoRetorna404() throws Exception {
        mockMvc.perform(delete("/api/pacientes/" + idPacienteRascunho)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(18)
    @DisplayName("DELETE paciente ativo deve retornar 204")
    void deletarPacienteAtivoRetorna204() throws Exception {
        mockMvc.perform(delete("/api/pacientes/" + idPacienteAtivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}

