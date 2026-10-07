package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.ItemPedidoRequest;
import org.example.models.Paciente;
import org.example.models.PedidoItem;
import org.example.repositories.PacienteRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PacienteService — regras de negócio")
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private EstoqueService estoqueService;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private ProdutoService produtoService;

    @InjectMocks
    private PacienteService pacienteService;

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Paciente novoPaciente(String nome, String status, Integer kits) {
        Paciente p = new Paciente();
        p.setNome(nome);
        p.setStatusResultado(status);
        p.setQuantidadeKits(kits);
        p.setCpf("529.982.247-25");
        p.setCodigoIdentificacao("PAC-001");
        return p;
    }

    private Paciente pacienteExistente(Long id, String nome, String status) {
        Paciente p = novoPaciente(nome, status, 1);
        p.setId(id);
        return p;
    }

    private void mockSalvar() {
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> {
            Paciente p = i.getArgument(0);
            if (p.getId() == null) p.setId(99L);
            return p;
        });
        when(pacienteRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    private ItemPedidoRequest itemPedido(long produtoId, int quantidade) {
        ItemPedidoRequest item = new ItemPedidoRequest();
        item.setProdutoId(produtoId);
        item.setQuantidade(quantidade);
        return item;
    }

    private PedidoItem itemGerado(String codigo) {
        PedidoItem item = new PedidoItem();
        item.setCodigoGerado(codigo);
        return item;
    }

    // ─── criarPaciente ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("criarPaciente")
    class CriarPaciente {

        @BeforeEach
        void setup() {
            mockSalvar();
            when(pacienteRepository.findAll()).thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("nome vazio deve lançar BadRequestException")
        void nomeVazioLancaBadRequest() {
            Paciente p = novoPaciente("", "Pendente", 1);
            assertThatThrownBy(() -> pacienteService.criarPaciente(p))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Nome");
        }

        @Test
        @DisplayName("nome nulo deve lançar BadRequestException")
        void nomeNuloLancaBadRequest() {
            Paciente p = novoPaciente(null, "Pendente", 1);
            assertThatThrownBy(() -> pacienteService.criarPaciente(p))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("CPF inválido deve lançar BadRequestException")
        void cpfInvalidoLancaBadRequest() {
            Paciente p = novoPaciente("Maria", "Rascunho", 1);
            p.setCpf("111.111.111-11");

            assertThatThrownBy(() -> pacienteService.criarPaciente(p))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("CPF");
        }

        @Test
        @DisplayName("CPF válido deve ser aceito")
        void cpfValidoDeveSerAceito() {
            Paciente p = novoPaciente("Maria", "Rascunho", 1);
            p.setCpf("529.982.247-25");

            assertThatCode(() -> pacienteService.criarPaciente(p)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rascunho não deve debitar estoque")
        void rascunhoNaoDebitaEstoque() {
            Paciente p = novoPaciente("João", "Rascunho", 1);
            pacienteService.criarPaciente(p);
            verify(estoqueService, never()).registrarMovimentacao(any(), any(), any());
        }

        @Test
        @DisplayName("rascunho deve ser salvo com status Rascunho")
        void rascunhoDeveSerSalvoComStatusRascunho() {
            Paciente p = novoPaciente("João", "Rascunho", 1);
            Paciente salvo = pacienteService.criarPaciente(p);
            assertThat(salvo.getStatusResultado()).isEqualTo("Rascunho");
        }

        @Test
        @DisplayName("paciente ativo com kits válidos deve debitar estoque")
        void ativoDeveDebitarEstoque() {
            Paciente p = novoPaciente("Maria", "Pendente", 2);
            p.setItensPedido(List.of(itemPedido(1L, 2)));
            when(produtoService.baixarEstoqueEGerarItens(any(Paciente.class), anyList()))
                    .thenReturn(List.of(itemGerado("10052026080001")));

            pacienteService.criarPaciente(p);
            verify(produtoService).baixarEstoqueEGerarItens(any(Paciente.class), anyList());
        }

        @Test
        @DisplayName("paciente ativo sem produtos deve lançar BadRequestException")
        void ativoSemProdutosLancaBadRequest() {
            Paciente p = novoPaciente("Maria", "Pendente", 1);
            assertThatThrownBy(() -> pacienteService.criarPaciente(p))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("produto");
        }

        @Test
        @DisplayName("paciente ativo com produto sem quantidade válida deve lançar BadRequestException")
        void ativoComQuantidadeInvalidaNoPedidoLancaBadRequest() {
            Paciente p = novoPaciente("Maria", "Pendente", 1);
            p.setItensPedido(List.of(itemPedido(1L, 0)));
            assertThatThrownBy(() -> pacienteService.criarPaciente(p))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("produto");
        }

        @Test
        @DisplayName("código enviado no payload de rascunho deve ser preservado")
        void codigoEnviadoNoPayloadRascunhoDeveSerPreservado() {
            Paciente novo = novoPaciente("Novo", "Rascunho", 1);
            novo.setCodigoIdentificacao("123456");
            Paciente salvo = pacienteService.criarPaciente(novo);
            assertThat(salvo.getCodigoIdentificacao()).isEqualTo("123456");
        }

        @Test
        @DisplayName("status nulo deve ser tratado como Pendente em paciente ativo")
        void statusNuloTratadoComoPendente() {
            Paciente p = novoPaciente("Ana", null, 1);
            p.setItensPedido(List.of(itemPedido(1L, 1)));
            when(produtoService.baixarEstoqueEGerarItens(any(Paciente.class), anyList()))
                    .thenReturn(List.of(itemGerado("10052026080003")));
            Paciente salvo = pacienteService.criarPaciente(p);
            assertThat(salvo.getStatusResultado()).isEqualTo("Pendente");
        }
    }

    // ─── atualizarPaciente ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("atualizarPaciente")
    class AtualizarPaciente {

        @BeforeEach
        void setup() {
            mockSalvar();
        }

        @Test
        @DisplayName("paciente não encontrado deve lançar NotFoundException")
        void naoEncontradoLancaNotFound() {
            when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());
            Paciente atualizado = novoPaciente("X", "Pendente", 1);
            assertThatThrownBy(() -> pacienteService.atualizarPaciente(99L, atualizado))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("paciente finalizado não pode ser editado")
        void finalizadoNaoPodeSerEditado() {
            Paciente existente = pacienteExistente(1L, "Ana", "Resultado pronto");
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.findAll()).thenReturn(List.of(existente));

            Paciente atualizado = novoPaciente("Ana modificada", "Resultado pronto", 1);
            assertThatThrownBy(() -> pacienteService.atualizarPaciente(1L, atualizado))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("finalizado");
        }

        @Test
        @DisplayName("paciente ativo não pode voltar para rascunho")
        void ativoNaoPodeVoltarParaRascunho() {
            Paciente existente = pacienteExistente(1L, "Ana", "Pendente");
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));

            Paciente atualizado = novoPaciente("Ana", "Rascunho", 1);
            assertThatThrownBy(() -> pacienteService.atualizarPaciente(1L, atualizado))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("rascunho");
        }

        @Test
        @DisplayName("nome deve ser atualizado quando novo valor fornecido")
        void nomeDeveSerAtualizado() {
            Paciente existente = pacienteExistente(1L, "Ana", "Pendente");
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.findAll()).thenReturn(List.of(existente));

            Paciente atualizado = new Paciente();
            atualizado.setNome("Ana Paula");

            Paciente resultado = pacienteService.atualizarPaciente(1L, atualizado);
            assertThat(resultado.getNome()).isEqualTo("Ana Paula");
        }

        @Test
        @DisplayName("campos nulos não devem sobrescrever valores existentes (merge)")
        void camposNulosNaoDevemsocrescrever() {
            Paciente existente = pacienteExistente(1L, "Ana", "Pendente");
            existente.setCpf("12345678901");
            existente.setEmail("ana@test.com");
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.findAll()).thenReturn(List.of(existente));

            Paciente atualizado = new Paciente();
            atualizado.setNome("Ana Paula");
            // cpf e email não enviados (null)

            Paciente resultado = pacienteService.atualizarPaciente(1L, atualizado);
            assertThat(resultado.getCpf()).isEqualTo("12345678901");
            assertThat(resultado.getEmail()).isEqualTo("ana@test.com");
        }

        @Test
        @DisplayName("rascunho ativado deve debitar estoque")
        void rascunhoAtivadoDeveDebitarEstoque() {
            Paciente existente = pacienteExistente(1L, "Ana", "Rascunho");
            existente.setCodigoIdentificacao(null);
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.findAll()).thenReturn(List.of(existente));
            when(produtoService.baixarEstoqueEGerarItens(any(Paciente.class), anyList()))
                    .thenReturn(List.of(itemGerado("10052026080002")));

            Paciente atualizado = novoPaciente("Ana", "Pendente", 2);
            atualizado.setItensPedido(List.of(itemPedido(1L, 2)));
            pacienteService.atualizarPaciente(1L, atualizado);

            verify(produtoService).baixarEstoqueEGerarItens(any(Paciente.class), anyList());
        }

        @Test
        @DisplayName("atualização de paciente já ativo não deve debitar estoque novamente")
        void ativoAtivoNaoDebitaEstoqueNovamente() {
            Paciente existente = pacienteExistente(1L, "Ana", "Pendente");
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.findAll()).thenReturn(List.of(existente));

            Paciente atualizado = novoPaciente("Ana", "Coleta em processo", 1);
            pacienteService.atualizarPaciente(1L, atualizado);

            verify(estoqueService, never()).registrarMovimentacao(any(), any(), any());
        }
    }

    // ─── deletarPaciente ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletarPaciente")
    class DeletarPaciente {

        @Test
        @DisplayName("paciente não encontrado deve lançar NotFoundException")
        void naoEncontradoLancaNotFound() {
            when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> pacienteService.deletarPaciente(99L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("paciente encontrado deve ser deletado do repositório")
        void deveDeletarDoBanco() {
            Paciente existente = pacienteExistente(5L, "Bob", "Pendente");
            when(pacienteRepository.findById(5L)).thenReturn(Optional.of(existente));
            doNothing().when(pacienteRepository).deleteById(5L);

            pacienteService.deletarPaciente(5L);
            verify(pacienteRepository).deleteById(5L);
        }
    }

    // ─── obterPacientePorId ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("obterPacientePorId")
    class ObterPorId {

        @Test
        @DisplayName("deve retornar Optional vazio para id inexistente")
        void deveRetornarVazioParaIdInexistente() {
            when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());
            assertThat(pacienteService.obterPacientePorId(99L)).isEmpty();
        }

        @Test
        @DisplayName("deve retornar o paciente para id existente")
        void deveRetornarPacienteParaIdExistente() {
            Paciente p = pacienteExistente(1L, "Ana", "Pendente");
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(p));
            when(pacienteRepository.save(any())).thenReturn(p);

            Optional<Paciente> resultado = pacienteService.obterPacientePorId(1L);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().getNome()).isEqualTo("Ana");
        }
    }

    // ─── fluxo de datas ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fluxo de datas — progressão sequencial")
    class FluxoDatas {

        @Test
        @DisplayName("criar paciente ativo deve preencher dataSaidaEstoque automaticamente")
        void criarAtivoDevePreencherDataSaida() {
            when(pacienteRepository.findAll()).thenReturn(Collections.emptyList());
            when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> {
                Paciente p = i.getArgument(0);
                if (p.getId() == null) p.setId(99L);
                return p;
            });
            Paciente p = novoPaciente("Ana", "Pendente", 1);
            p.setItensPedido(List.of(itemPedido(1L, 1)));
            when(produtoService.baixarEstoqueEGerarItens(any(Paciente.class), anyList()))
                    .thenReturn(List.of(itemGerado("10052026080004")));
            Paciente salvo = pacienteService.criarPaciente(p);
            assertThat(salvo.getDataSaidaEstoque()).isNotBlank();
        }

        @Test
        @DisplayName("tentar enviar dataEntrega sem dataSaidaEstoque deve lançar ConflictException")
        void tentarAvancarSemDataSaidaLancaConflict() {
            Paciente existente = pacienteExistente(1L, "Ana", "Pendente");
            existente.setDataSaidaEstoque(null);
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.findAll()).thenReturn(List.of(existente));
            mockSalvar();

            Paciente atualizado = new Paciente();
            atualizado.setNome("Ana");
            atualizado.setDataEntrega("2026-07-01T10:00:00");

            assertThatThrownBy(() -> pacienteService.atualizarPaciente(1L, atualizado))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Kit saiu do estoque");
        }

        @Test
        @DisplayName("não deve avançar coleta em processo sem dataEntrega")
        void naoDeveAvancarColetaSemDataEntrega() {
            Paciente existente = pacienteExistente(1L, "Ana", "Pendente");
            existente.setDataSaidaEstoque("2026-07-01T10:00:00");
            existente.setDataEntrega(null);
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(pacienteRepository.findAll()).thenReturn(List.of(existente));
            mockSalvar();

            Paciente atualizado = new Paciente();
            atualizado.setNome("Ana");
            atualizado.setDataColetaProcesso("2026-07-02T10:00:00");
            assertThatThrownBy(() -> pacienteService.atualizarPaciente(1L, atualizado))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Coleta em processo");
        }
    }
}
