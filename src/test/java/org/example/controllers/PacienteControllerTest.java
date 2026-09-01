package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.Paciente;
import org.example.models.Usuario;
import org.example.repositories.PacienteRepository;
import org.example.repositories.UsuarioRepository;
import org.example.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PacienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        pacienteRepository.deleteAll();
        if (usuarioRepository.findByUsername("admin-test").isEmpty()) {
            Usuario u = new Usuario();
            u.setUsername("admin-test");
            u.setSenha(passwordEncoder.encode("Test@12345"));
            u.setRole("ROLE_ADMIN");
            usuarioRepository.save(u);
        }
        adminToken = jwtUtil.gerarToken("admin-test", "ROLE_ADMIN");
    }

    @Test
    void listarPacientes_semAuth_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/pacientes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarPacientes_comAuth_deveRetornarLista() throws Exception {
        mockMvc.perform(get("/api/pacientes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listarPacientes_comPaginacao_deveRetornarPage() throws Exception {
        mockMvc.perform(get("/api/pacientes?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    @Test
    void criarPaciente_comDadosValidos_deveRetornar201() throws Exception {
        Paciente p = new Paciente();
        p.setNome("Paciente Teste");
        p.setStatusResultado("Rascunho");

        mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Paciente Teste"));
    }

    @Test
    void criarPaciente_semNome_deveRetornar400() throws Exception {
        Paciente p = new Paciente();
        p.setStatusResultado("Rascunho");

        mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obterPacientePorId_inexistente_deveRetornar404() throws Exception {
        mockMvc.perform(get("/api/pacientes/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletarPaciente_semRole_deveRetornar403() throws Exception {
        Paciente p = new Paciente();
        p.setNome("Para Deletar");
        p.setStatusResultado("Rascunho");
        Paciente salvo = pacienteRepository.save(p);

        // Tenta deletar com role LEITURA (sem permissão)
        String leituraToken = jwtUtil.gerarToken("leitor", "ROLE_LEITURA");
        mockMvc.perform(delete("/api/pacientes/" + salvo.getId())
                        .header("Authorization", "Bearer " + leituraToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletarPaciente_comRoleAdmin_deveRetornar204() throws Exception {
        Paciente p = new Paciente();
        p.setNome("Para Deletar Admin");
        p.setStatusResultado("Rascunho");
        Paciente salvo = pacienteRepository.save(p);

        mockMvc.perform(delete("/api/pacientes/" + salvo.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
