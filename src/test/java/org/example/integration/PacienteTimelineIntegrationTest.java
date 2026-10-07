package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("Paciente timeline — integração")
class PacienteTimelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;

    @BeforeAll
    static void obterToken(@Autowired MockMvc mockMvc,
                           @Autowired ObjectMapper objectMapper) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin", "senha", "Admin@12345"))))
                .andExpect(status().isOk())
                .andReturn();
        token = result.getResponse().getCookie("euroespes_access").getValue();
    }

    @Test
    @DisplayName("PUT sequencial deve avançar fluxo e devolver campos da timeline no payload de resposta")
    void putSequencialDeveAvancarFluxoComContratoEstavel() throws Exception {
        long produtoId = criarProduto("TL-101");
        long pacienteId = criarPacienteAtivo("Paciente Timeline Contrato", produtoId);

        mockMvc.perform(put("/api/pacientes/" + pacienteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEntrega", "2026-10-01T10:00",
                                "dataColetaProcesso", "2026-10-02T10:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Paciente Timeline Contrato"))
                .andExpect(jsonPath("$.dataEntrega").value("2026-10-01T10:00"))
                .andExpect(jsonPath("$.dataColetaProcesso").value("2026-10-02T10:00"))
                .andExpect(jsonPath("$.statusResultado").value("Coleta em processo"));

        mockMvc.perform(put("/api/pacientes/" + pacienteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataColetaRealizada", "2026-10-03T10:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataSaidaEstoque").isNotEmpty())
                .andExpect(jsonPath("$.dataEntrega").value("2026-10-01T10:00"))
                .andExpect(jsonPath("$.dataColetaProcesso").value("2026-10-02T10:00"))
                .andExpect(jsonPath("$.dataColetaRealizada").value("2026-10-03T10:00"))
                .andExpect(jsonPath("$.statusResultado").value("Coleta realizada"));
    }

    @Test
    @DisplayName("PUT não deve permitir pular etapas da timeline")
    void putNaoDevePermitirPularEtapas() throws Exception {
        long produtoId = criarProduto("TL-102");
        long pacienteId = criarPacienteAtivo("Paciente Timeline Bloqueio", produtoId);

        mockMvc.perform(put("/api/pacientes/" + pacienteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEmMaoBrasil", "2026-10-05T10:00"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Coleta realizada")));
    }

    @Test
    @DisplayName("PUT ao voltar etapa para pendente deve limpar etapas posteriores")
    void putRollbackDeveLimparEtapasPosteriores() throws Exception {
        long produtoId = criarProduto("TL-103");
        long pacienteId = criarPacienteAtivo("Paciente Timeline Rollback", produtoId);

        mockMvc.perform(put("/api/pacientes/" + pacienteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEntrega", "2026-10-01T10:00",
                                "dataColetaProcesso", "2026-10-02T10:00",
                                "dataColetaRealizada", "2026-10-03T10:00",
                                "dataEmMaoBrasil", "2026-10-04T10:00",
                                "dataEnviadoEspanha", "2026-10-05T10:00",
                                "codigoRastreio", "TL-103-TRACK"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusResultado").value("Enviado para Euroespes Espanha"));

        mockMvc.perform(put("/api/pacientes/" + pacienteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataColetaRealizada", ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataColetaRealizada").value(""))
                .andExpect(jsonPath("$.dataEmMaoBrasil").value(nullValue()))
                .andExpect(jsonPath("$.dataEnviadoEspanha").value(nullValue()))
                .andExpect(jsonPath("$.statusResultado").value("Coleta em processo"));
    }

    private long criarProduto(String codigoProduto) throws Exception {
        MvcResult produtoResult = mockMvc.perform(post("/api/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Kit Timeline " + codigoProduto,
                                "codigoProduto", codigoProduto,
                                "codigoSerie", "A1",
                                "estoqueAtual", 50,
                                "estoqueMinimo", 5
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(produtoResult.getResponse().getContentAsString()).get("id").asLong();
    }

    private long criarPacienteAtivo(String nome, long produtoId) throws Exception {
        MvcResult pacienteResult = mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", nome,
                                "cpf", "529.982.247-25",
                                "telefone", "(11) 99999-0001",
                                "codigoIdentificacao", "TIMELINE-" + produtoId,
                                "itensPedido", List.of(Map.of("produtoId", produtoId, "quantidade", 1))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(pacienteResult.getResponse().getContentAsString()).get("id").asLong();
    }
}
