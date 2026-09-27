package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.Usuario;
import org.example.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        if (usuarioRepository.findByUsername("testuser").isEmpty()) {
            Usuario u = new Usuario();
            u.setUsername("testuser");
            u.setSenha(passwordEncoder.encode("Test@12345"));
            u.setRole("ROLE_ADMIN");
            usuarioRepository.save(u);
        }
    }

    @Test
    void login_comCredenciaisValidas_deveRetornarCookieSeguro() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "testuser", "senha", "Test@12345"))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("euroespes_access"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void login_comSenhaErrada_deveRetornar401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "testuser", "senha", "SenhaErrada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_semCredenciais_deveRetornar400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
        void refresh_comSessaoValida_deveRotacionarCookie() throws Exception {
        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "testuser", "senha", "Test@12345"))))
                .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(login.getResponse().getCookie("euroespes_access")))
                .andExpect(status().isOk())
            .andExpect(cookie().exists("euroespes_access"));
    }

    @Test
    void refresh_comTokenInvalido_deveRetornar401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "token.invalido"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_comUsuarioInativo_deveRetornar401() throws Exception {
        Usuario inativo = new Usuario();
        inativo.setUsername("inativo");
        inativo.setSenha(passwordEncoder.encode("Test@12345"));
        inativo.setRole("ROLE_ADMIN");
        inativo.setAtivo(false);
        usuarioRepository.save(inativo);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "inativo", "senha", "Test@12345"))))
                .andExpect(status().isUnauthorized());
    }
}
