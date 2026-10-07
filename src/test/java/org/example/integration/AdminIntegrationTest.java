package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.Medico;
import org.example.models.Produto;
import org.example.models.Usuario;
import org.example.repositories.MedicoEstoqueRepository;
import org.example.repositories.MedicoRepository;
import org.example.repositories.ProdutoRepository;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MedicoRepository medicoRepository;

    @Autowired
    private MedicoEstoqueRepository medicoEstoqueRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setup() {
        medicoEstoqueRepository.deleteAll();
        medicoRepository.deleteAll();
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
    void listarPermissoes_deveRetornarLista() throws Exception {
        mockMvc.perform(get("/api/admin/permissoes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void listarUsuarios_deveRetornarPermissoesSemErroLazy() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].permissoes").isArray());
    }

    @Test
    void criarMedico_devePersistir() throws Exception {
        mockMvc.perform(post("/api/medicos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Dra. Ana",
                                  "crm": "12345",
                                  "cpf": "52998224725",
                                  "email": "ana@example.com",
                                  "especialidade": "Clínica Geral",
                                  "estoquePrivado": true,
                                  "ativo": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Dra. Ana"))
                .andExpect(jsonPath("$.crm").value("12345"));
    }

    @Test
    void criarMedico_semCamposObrigatorios_deveRetornar400() throws Exception {
        mockMvc.perform(post("/api/medicos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Dra. Sem Campos",
                                  "email": "semcampos@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarMedicos_semToken_deveBloquear() throws Exception {
        mockMvc.perform(get("/api/medicos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void criarUsuario_adminDeveAceitar() throws Exception {
        mockMvc.perform(post("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", "operador1",
                                "senha", "Operador@123",
                                "role", "ROLE_LEITURA",
                                "ativo", true,
                                "permissoes", java.util.List.of("dashboard.read", "pacientes.read")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("operador1"))
                .andExpect(jsonPath("$.permissoes.length()").value(2));
    }

    @Test
    void removerUsuario_adminDeveAceitar() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setUsername("operador-remover");
        usuario.setSenha(passwordEncoder.encode("Operador@123"));
        usuario.setRole("ROLE_LEITURA");
        usuario.setAtivo(true);
        Usuario salvo = usuarioRepository.save(usuario);

        mockMvc.perform(delete("/api/admin/usuarios/" + salvo.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void listarEstoqueDoMedico_deveRetornarProdutoSemErroLazy() throws Exception {
        Medico medico = new Medico();
        medico.setNome("Dra. Estoque");
        medico.setCpf("39053344705");
        medico.setCrm("CRM-9001");
        medico.setEspecialidade("Cardiologia");
        Medico medicoSalvo = medicoRepository.save(medico);

        Produto produto = new Produto();
        produto.setNome("Produto Estoque");
        produto.setCodigoProduto("P" + System.nanoTime());
        produto.setCodigoSerie("S" + System.nanoTime());
        produto.setEstoqueAtual(10);
        produto.setEstoqueMinimo(1);
        Produto produtoSalvo = produtoRepository.save(produto);

        mockMvc.perform(post("/api/medicos/" + medicoSalvo.getId() + "/estoque")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "produtoId", produtoSalvo.getId(),
                                "delta", 3,
                                "estoqueMinimo", 1
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/medicos/" + medicoSalvo.getId() + "/estoque")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].produtoId").value(produtoSalvo.getId()))
                .andExpect(jsonPath("$[0].produtoNome").value("Produto Estoque"));
    }
}
