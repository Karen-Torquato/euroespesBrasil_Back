package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.models.Estoque;
import org.example.models.MovimentacaoEstoque;
import org.example.repositories.EstoqueRepository;
import org.example.repositories.MovimentacaoEstoqueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EstoqueService — regras de negócio")
class EstoqueServiceTest {

    @Mock
    private EstoqueRepository estoqueRepository;

    @Mock
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    @InjectMocks
    private EstoqueService estoqueService;

    private Estoque estoqueComKits(int kits) {
        Estoque e = new Estoque();
        e.setId(1L);
        e.setTotalKits(kits);
        return e;
    }

    private void mockEstoque(int kits) {
        when(estoqueRepository.count()).thenReturn(1L);
        when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(kits)));
        when(estoqueRepository.save(any(Estoque.class))).thenAnswer(i -> i.getArgument(0));
        when(movimentacaoRepository.save(any(MovimentacaoEstoque.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ─── obterEstoque ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("obterEstoque")
    class ObterEstoque {

        @Test
        @DisplayName("deve criar estoque com 100 kits quando não existe nenhum")
        void deveCriarEstoqueInicialQuandoVazio() {
            when(estoqueRepository.count()).thenReturn(0L);
            Estoque novo = new Estoque();
            novo.setTotalKits(100);
            when(estoqueRepository.save(any())).thenReturn(novo);

            Estoque resultado = estoqueService.obterEstoque();
            assertThat(resultado.getTotalKits()).isEqualTo(100);
            verify(estoqueRepository).save(any(Estoque.class));
        }

        @Test
        @DisplayName("deve retornar o estoque existente quando já existe")
        void deveRetornarEstoqueExistente() {
            when(estoqueRepository.count()).thenReturn(1L);
            when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(42)));

            Estoque resultado = estoqueService.obterEstoque();
            assertThat(resultado.getTotalKits()).isEqualTo(42);
        }
    }

    // ─── registrarMovimentacao — ENTRADA ────────────────────────────────────────

    @Nested
    @DisplayName("registrarMovimentacao — ENTRADA")
    class Entrada {

        @Test
        @DisplayName("ENTRADA válida deve aumentar o estoque")
        void entradaDeveAumentarEstoque() {
            mockEstoque(50);
            estoqueService.registrarMovimentacao("ENTRADA", 10, "Reposição");

            verify(estoqueRepository).save(argThat(e -> e.getTotalKits() == 60));
        }

        @Test
        @DisplayName("ENTRADA com tipo em minúsculo deve funcionar (normalização)")
        void entradaMinusculoDeveNormalizar() {
            mockEstoque(10);
            assertThatCode(() -> estoqueService.registrarMovimentacao("entrada", 5, "teste"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ENTRADA deve registrar movimentação com tipo ENTRADA")
        void entradaDeveRegistrarMovimentacao() {
            mockEstoque(10);
            estoqueService.registrarMovimentacao("ENTRADA", 5, "Reposição mensal");

            verify(movimentacaoRepository).save(argThat(m ->
                    "ENTRADA".equals(m.getTipo()) && m.getQuantidade() == 5));
        }
    }

    // ─── registrarMovimentacao — SAIDA ───────────────────────────────────────────

    @Nested
    @DisplayName("registrarMovimentacao — SAIDA")
    class Saida {

        @Test
        @DisplayName("SAIDA válida deve reduzir o estoque")
        void saidaDeveReduzirEstoque() {
            mockEstoque(50);
            estoqueService.registrarMovimentacao("SAIDA", 10, "Retirada");

            verify(estoqueRepository).save(argThat(e -> e.getTotalKits() == 40));
        }

        @Test
        @DisplayName("SAIDA com quantidade exata do estoque deve ser permitida")
        void saidaExataDevePermitir() {
            mockEstoque(10);
            assertThatCode(() -> estoqueService.registrarMovimentacao("SAIDA", 10, "Tudo"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SAIDA maior que o estoque deve lançar ConflictException")
        void saidaMaiorQueEstoqueLancaConflict() {
            mockEstoque(5);
            assertThatThrownBy(() -> estoqueService.registrarMovimentacao("SAIDA", 10, "Sem estoque"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("insuficiente");
        }

        @Test
        @DisplayName("SAIDA deve registrar movimentação com tipo SAIDA")
        void saidaDeveRegistrarMovimentacao() {
            mockEstoque(20);
            estoqueService.registrarMovimentacao("SAIDA", 3, "Para paciente");

            verify(movimentacaoRepository).save(argThat(m ->
                    "SAIDA".equals(m.getTipo()) && m.getQuantidade() == 3));
        }
    }

    // ─── registrarMovimentacao — validações ─────────────────────────────────────

    @Nested
    @DisplayName("registrarMovimentacao — validações de entrada")
    class ValidacoesEntrada {

        @Test
        @DisplayName("tipo inválido deve lançar BadRequestException")
        void tipoInvalidoLancaBadRequest() {
            assertThatThrownBy(() -> estoqueService.registrarMovimentacao("INVALIDO", 5, ""))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ENTRADA");
        }

        @Test
        @DisplayName("tipo nulo deve lançar BadRequestException")
        void tipoNuloLancaBadRequest() {
            assertThatThrownBy(() -> estoqueService.registrarMovimentacao(null, 5, ""))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("quantidade nula deve lançar BadRequestException")
        void quantidadeNulaLancaBadRequest() {
            assertThatThrownBy(() -> estoqueService.registrarMovimentacao("ENTRADA", null, ""))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maior que zero");
        }

        @Test
        @DisplayName("quantidade zero deve lançar BadRequestException")
        void quantidadeZeroLancaBadRequest() {
            assertThatThrownBy(() -> estoqueService.registrarMovimentacao("ENTRADA", 0, ""))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("quantidade negativa deve lançar BadRequestException")
        void quantidadeNegativaLancaBadRequest() {
            assertThatThrownBy(() -> estoqueService.registrarMovimentacao("ENTRADA", -1, ""))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("motivo nulo deve ser tratado como string vazia")
        void motivoNuloDeveSerTratado() {
            mockEstoque(10);
            assertThatCode(() -> estoqueService.registrarMovimentacao("ENTRADA", 5, null))
                    .doesNotThrowAnyException();
        }
    }

    // ─── isEstoqueBaixo / isEstoqueCritico ───────────────────────────────────────

    @Nested
    @DisplayName("alertas de estoque")
    class AlertasEstoque {

        @Test
        @DisplayName("estoque com 5 kits deve ser considerado baixo")
        void cincoKitsEhEstoqueBaixo() {
            when(estoqueRepository.count()).thenReturn(1L);
            when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(5)));
            assertThat(estoqueService.isEstoqueBaixo()).isTrue();
        }

        @Test
        @DisplayName("estoque com 3 kits deve ser considerado baixo")
        void tresKitsEhEstoqueBaixo() {
            when(estoqueRepository.count()).thenReturn(1L);
            when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(3)));
            assertThat(estoqueService.isEstoqueBaixo()).isTrue();
        }

        @Test
        @DisplayName("estoque com 6 kits NÃO deve ser considerado baixo")
        void seisKitsNaoEhEstoqueBaixo() {
            when(estoqueRepository.count()).thenReturn(1L);
            when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(6)));
            assertThat(estoqueService.isEstoqueBaixo()).isFalse();
        }

        @Test
        @DisplayName("estoque com 2 kits deve ser considerado crítico")
        void doisKitsEhEstoqueCritico() {
            when(estoqueRepository.count()).thenReturn(1L);
            when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(2)));
            assertThat(estoqueService.isEstoqueCritico()).isTrue();
        }

        @Test
        @DisplayName("estoque com 0 kits deve ser considerado crítico")
        void zeroKitsEhEstoqueCritico() {
            when(estoqueRepository.count()).thenReturn(1L);
            when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(0)));
            assertThat(estoqueService.isEstoqueCritico()).isTrue();
        }

        @Test
        @DisplayName("estoque com 3 kits NÃO deve ser considerado crítico")
        void tresKitsNaoEhEstoqueCritico() {
            when(estoqueRepository.count()).thenReturn(1L);
            when(estoqueRepository.findAll()).thenReturn(List.of(estoqueComKits(3)));
            assertThat(estoqueService.isEstoqueCritico()).isFalse();
        }
    }

    // ─── decrementarEstoque ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("decrementarEstoque")
    class DecrementarEstoque {

        @Test
        @DisplayName("deve chamar registrarMovimentacao com tipo SAIDA")
        void deveChamarSaida() {
            mockEstoque(20);
            estoqueService.decrementarEstoque(5);
            verify(movimentacaoRepository).save(argThat(m -> "SAIDA".equals(m.getTipo())));
        }

        @Test
        @DisplayName("deve lançar ConflictException quando estoque insuficiente")
        void deveLancarQuandoInsuficiente() {
            mockEstoque(2);
            assertThatThrownBy(() -> estoqueService.decrementarEstoque(5))
                    .isInstanceOf(ConflictException.class);
        }
    }
}

