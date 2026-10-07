package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.models.Produto;
import org.example.models.Usuario;
import org.example.repositories.MovimentacaoEstoqueRepository;
import org.example.repositories.PedidoItemRepository;
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

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProdutoAuditoriaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;

    @Autowired
    private PedidoItemRepository pedidoItemRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setup() {
        pedidoItemRepository.deleteAll();
        movimentacaoEstoqueRepository.deleteAll();
        produtoRepository.deleteAll();
        if (usuarioRepository.findByUsername("admin-produto-test").isEmpty()) {
            Usuario usuario = new Usuario();
            usuario.setUsername("admin-produto-test");
            usuario.setSenha(passwordEncoder.encode("Test@12345"));
            usuario.setRole("ROLE_ADMIN");
            usuarioRepository.save(usuario);
        }
        adminToken = jwtUtil.gerarToken("admin-produto-test", "ROLE_ADMIN");
    }

    @Test
    void auditoriaDeProduto_deveListarMovimentacoes() throws Exception {
        Produto produto = new Produto();
        produto.setNome("Kit Auditoria");
        produto.setCodigoProduto("91");
        produto.setCodigoSerie("A1");
        produto.setEstoqueAtual(5);
        produto.setEstoqueMinimo(1);
        Produto salvo = produtoRepository.save(produto);

        mockMvc.perform(post("/api/produtos/" + salvo.getId() + "/ajustar-estoque")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("delta", 2))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/produtos/" + salvo.getId() + "/auditoria")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].tipo").exists())
                .andExpect(jsonPath("$[0].quantidade").exists())
                .andExpect(jsonPath("$[0].usuario").value("admin-produto-test"))
                .andExpect(jsonPath("$[0].registradoEm").value(not(blankOrNullString())));
    }
}
