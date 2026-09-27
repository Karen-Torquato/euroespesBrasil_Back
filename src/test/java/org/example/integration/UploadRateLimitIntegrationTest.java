package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.upload.max-attempts=2",
        "app.security.upload.window-seconds=60"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Upload rate-limit — integração")
class UploadRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("deve retornar 429 após exceder tentativas de upload")
    void deveRetornar429QuandoExcederUploads() throws Exception {
        String token = loginEObterToken();
        long pacienteId = criarPaciente(token);

        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "exame.pdf", "application/pdf", "%PDF-1.4 Conteudo".getBytes()
        );

        mockMvc.perform(multipart("/api/pacientes/" + pacienteId + "/anexo")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/pacientes/" + pacienteId + "/anexo")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/pacientes/" + pacienteId + "/anexo")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").exists());
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

    private long criarPaciente(String token) throws Exception {
        MvcResult create = mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Paciente Upload Rate",
                                "statusResultado", "Rascunho"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asLong();
    }
}
