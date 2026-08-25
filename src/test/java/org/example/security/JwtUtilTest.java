package org.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtil — geração e validação de tokens JWT")
class JwtUtilTest {

    // Segredo com 64+ chars (requisito HS512)
    private static final String SECRET =
            "ChaveDeTeste_MuitoLonga_ParaOsTestesSemPreExigir64CaracteresOuMaisOk!";
    private static final long EXPIRACAO_MS = 3_600_000L; // 1 hora

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRACAO_MS);
    }

    // ─── gerarToken ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gerarToken")
    class GerarToken {

        @Test
        @DisplayName("deve gerar token não nulo e não vazio")
        void deveGerarTokenNaoVazio() {
            String token = jwtUtil.gerarToken("alice", "ROLE_ADMIN");
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("token gerado deve ser válido imediatamente")
        void tokenGeradoDeveSerValido() {
            String token = jwtUtil.gerarToken("alice", "ROLE_ADMIN");
            assertThat(jwtUtil.isTokenValido(token)).isTrue();
        }

        @Test
        @DisplayName("dois tokens gerados para o mesmo usuário devem ser diferentes (iat diferente)")
        void doisTokensDevemSerDiferentes() throws InterruptedException {
            String t1 = jwtUtil.gerarToken("alice", "ROLE_ADMIN");
            Thread.sleep(1100); // garante iat diferente (precisão de segundos no JWT)
            String t2 = jwtUtil.gerarToken("alice", "ROLE_ADMIN");
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    // ─── extrairUsername ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extrairUsername")
    class ExtrairUsername {

        @Test
        @DisplayName("deve retornar o username correto do token")
        void deveExtrairUsernameCorreto() {
            String token = jwtUtil.gerarToken("maria", "ROLE_USER");
            assertThat(jwtUtil.extrairUsername(token)).isEqualTo("maria");
        }

        @Test
        @DisplayName("deve preservar username com caracteres especiais")
        void devePreservarUsernameComEspeciais() {
            String token = jwtUtil.gerarToken("usuario@exemplo.com", "ROLE_USER");
            assertThat(jwtUtil.extrairUsername(token)).isEqualTo("usuario@exemplo.com");
        }
    }

    // ─── extrairRole ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extrairRole")
    class ExtrairRole {

        @Test
        @DisplayName("deve retornar ROLE_ADMIN corretamente")
        void deveExtrairRoleAdmin() {
            String token = jwtUtil.gerarToken("alice", "ROLE_ADMIN");
            assertThat(jwtUtil.extrairRole(token)).isEqualTo("ROLE_ADMIN");
        }

        @Test
        @DisplayName("deve retornar ROLE_USER corretamente")
        void deveExtrairRoleUser() {
            String token = jwtUtil.gerarToken("bob", "ROLE_USER");
            assertThat(jwtUtil.extrairRole(token)).isEqualTo("ROLE_USER");
        }
    }

    // ─── validarToken ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validarToken")
    class ValidarToken {

        @Test
        @DisplayName("deve retornar Claims para token válido")
        void deveRetornarClaimsParaTokenValido() {
            String token = jwtUtil.gerarToken("admin", "ROLE_ADMIN");
            Claims claims = jwtUtil.validarToken(token);
            assertThat(claims.getSubject()).isEqualTo("admin");
        }

        @Test
        @DisplayName("deve lançar JwtException para token adulterado")
        void deveLancarExcecaoParaTokenAdulterado() {
            String token = jwtUtil.gerarToken("admin", "ROLE_ADMIN");
            String adulterado = token.substring(0, token.length() - 5) + "XXXXX";
            assertThatThrownBy(() -> jwtUtil.validarToken(adulterado))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("deve lançar exceção para token completamente inválido")
        void deveLancarExcecaoParaTokenInvalido() {
            assertThatThrownBy(() -> jwtUtil.validarToken("nao.e.um.jwt"))
                    .isInstanceOf(Exception.class);
        }
    }

    // ─── isTokenValido ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isTokenValido")
    class IsTokenValido {

        @Test
        @DisplayName("deve retornar true para token correto")
        void deveRetornarTrueParaTokenCorreto() {
            String token = jwtUtil.gerarToken("user", "ROLE_USER");
            assertThat(jwtUtil.isTokenValido(token)).isTrue();
        }

        @Test
        @DisplayName("deve retornar false para string vazia")
        void deveRetornarFalseParaStringVazia() {
            assertThat(jwtUtil.isTokenValido("")).isFalse();
        }

        @Test
        @DisplayName("deve retornar false para string aleatória")
        void deveRetornarFalseParaStringAleatoria() {
            assertThat(jwtUtil.isTokenValido("qualquer.coisa.invalida")).isFalse();
        }

        @Test
        @DisplayName("deve retornar false para token expirado")
        void deveRetornarFalseParaTokenExpirado() {
            JwtUtil jwtCurto = new JwtUtil(SECRET, 1L); // expira em 1ms
            String token = jwtCurto.gerarToken("user", "ROLE_USER");
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            assertThat(jwtCurto.isTokenValido(token)).isFalse();
        }

        @Test
        @DisplayName("deve retornar false para token assinado com segredo diferente")
        void deveRetornarFalseParaTokenDeOutroSegredo() {
            JwtUtil outro = new JwtUtil(
                    "OutroSegredoTotalmenteDistintoQueNaoDeveSerAceito64CharsOk!!!", EXPIRACAO_MS);
            String tokenDeOutro = outro.gerarToken("hacker", "ROLE_ADMIN");
            assertThat(jwtUtil.isTokenValido(tokenDeOutro)).isFalse();
        }
    }
}

