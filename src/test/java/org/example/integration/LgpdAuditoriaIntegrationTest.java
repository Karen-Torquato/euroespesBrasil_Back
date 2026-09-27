package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.AuditoriaEvento;
import org.example.repositories.AuditoriaEventoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("LGPD + Auditoria — integração")
class LgpdAuditoriaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditoriaEventoRepository auditoriaEventoRepository;

    @Test
    @DisplayName("exportar e anonimizar paciente deve registrar auditoria e limpar dados sensíveis")
    void exportarEAnonimizarDeveRegistrarAuditoria() throws Exception {
        String token = loginEObterToken();

        MvcResult createResult = mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Paciente LGPD",
                                "cpf", "529.982.247-25",
                                "telefone", "11988887777",
                                "email", "lgpd@teste.com",
                                "endereco", "Rua LGPD, 123",
                                "statusResultado", "Rascunho"))))
                .andExpect(status().isCreated())
                .andReturn();
        long pacienteId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/pacientes/" + pacienteId + "/exportar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Paciente LGPD"))
                .andExpect(jsonPath("$.email").value("lgpd@teste.com"));

        mockMvc.perform(delete("/api/pacientes/" + pacienteId + "/anonimizar")
                        .queryParam("motivo", "Teste LGPD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/pacientes/" + pacienteId + "/exportar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Paciente anonimizado"))
                .andExpect(jsonPath("$.cpf").value(nullValue()))
                .andExpect(jsonPath("$.telefone").value(nullValue()))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.endereco").value(nullValue()));

        assertThat(existeEvento("EXPORTAR", "PACIENTE", pacienteId)).isTrue();
        assertThat(existeEvento("ANONIMIZAR", "PACIENTE", pacienteId)).isTrue();
    }

    private boolean existeEvento(String acao, String recurso, long recursoId) {
        return auditoriaEventoRepository.findAll().stream()
                .anyMatch(evento -> mesmaAcaoRecurso(evento, acao, recurso, recursoId));
    }

    private boolean mesmaAcaoRecurso(AuditoriaEvento evento, String acao, String recurso, long recursoId) {
        return acao.equals(evento.getAcao())
                && recurso.equals(evento.getRecurso())
                && evento.getRecursoId() != null
                && evento.getRecursoId() == recursoId;
    }

    private String loginEObterToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin", "senha", "Admin@12345"))))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie("euroespes_access").getValue();
    }
}
