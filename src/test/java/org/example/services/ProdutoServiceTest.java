package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.ItemPedidoRequest;
import org.example.models.Paciente;
import org.example.models.PedidoItem;
import org.example.models.Produto;
import org.example.repositories.PedidoItemRepository;
import org.example.repositories.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private PedidoItemRepository pedidoItemRepository;

    @InjectMocks
    private ProdutoService service;

    @Test
    void listsAndLoadsProducts() {
        Produto product = product(1L, 5, 2, 0);
        when(produtoRepository.findAll()).thenReturn(List.of(product));
        when(produtoRepository.findProdutosEmEstoqueBaixo()).thenReturn(List.of(product));
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThat(service.listarTodos()).containsExactly(product);
        assertThat(service.listarProdutosEmEstoqueBaixo()).containsExactly(product);
        assertThat(service.obterPorId(1L)).isSameAs(product);
        assertThatThrownBy(() -> service.obterPorId(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void createsProductWithDefaultsAndResetsSequence() {
        Produto input = product(null, -1, null, 99);
        when(produtoRepository.findByCodigoProdutoAndCodigoSerie("05", "SERIE-A")).thenReturn(Optional.empty());
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Produto result = service.criarProduto(input);

        assertThat(result.getEstoqueAtual()).isZero();
        assertThat(result.getEstoqueMinimo()).isEqualTo(5);
        assertThat(result.getUltimaSequencia()).isZero();
        verify(produtoRepository).save(input);
    }

    @Test
    void rejectsMissingFieldsAndDuplicateCode() {
        assertThatThrownBy(() -> service.criarProduto(productInput(" ", "05", "SERIE-A")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.criarProduto(productInput("Kit", "", "SERIE-A")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.criarProduto(productInput("Kit", "05", " ")))
                .isInstanceOf(BadRequestException.class);

        when(produtoRepository.findByCodigoProdutoAndCodigoSerie("05", "SERIE-A"))
                .thenReturn(Optional.of(product(1L, 1, 1, 0)));
        assertThatThrownBy(() -> service.criarProduto(productInput("Kit", "05", "SERIE-A")))
                .isInstanceOf(ConflictException.class);
        verify(produtoRepository, never()).save(any());
    }

    @Test
    void updatesOnlyValidNonBlankFields() {
        Produto current = product(4L, 3, 2, 7);
        when(produtoRepository.findById(4L)).thenReturn(Optional.of(current));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Produto patch = new Produto();
        patch.setNome("Novo nome");
        patch.setCodigoProduto(" ");
        patch.setEstoqueAtual(-4);
        patch.setEstoqueMinimo(9);

        Produto result = service.atualizarProduto(4L, patch);

        assertThat(result.getNome()).isEqualTo("Novo nome");
        assertThat(result.getCodigoProduto()).isEqualTo("05");
        assertThat(result.getEstoqueAtual()).isEqualTo(3);
        assertThat(result.getEstoqueMinimo()).isEqualTo(9);
        assertThatThrownBy(() -> service.atualizarProduto(99L, patch)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void adjustsStockWithLockAndRejectsNegativeResult() {
        Produto current = product(2L, 1, 1, 0);
        when(produtoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(current));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.ajustarEstoque(2L, 4);
        assertThat(current.getEstoqueAtual()).isEqualTo(5);
        assertThatThrownBy(() -> service.ajustarEstoque(2L, -6)).isInstanceOf(ConflictException.class);
        when(produtoRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ajustarEstoque(99L, 1)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void validatesRequestedItemsAndAvailableStock() {
        Paciente patient = new Paciente();
        ItemPedidoRequest invalid = request(null, 1);
        assertThatThrownBy(() -> service.baixarEstoqueEGerarItens(patient, List.of(invalid)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.baixarEstoqueEGerarItens(patient, List.of(request(1L, 0))))
                .isInstanceOf(BadRequestException.class);

        when(produtoRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.baixarEstoqueEGerarItens(patient, List.of(request(9L, 1))))
                .isInstanceOf(NotFoundException.class);
        when(produtoRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product(10L, 0, 1, 0)));
        assertThatThrownBy(() -> service.baixarEstoqueEGerarItens(patient, List.of(request(10L, 1))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void decrementsStockAndGeneratesOneUniqueCodePerUnit() {
        Paciente patient = new Paciente();
        Produto product = product(8L, 3, 1, 4);
        when(produtoRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(product));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoItemRepository.save(any(PedidoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<PedidoItem> generated = service.baixarEstoqueEGerarItens(patient, List.of(request(8L, 2)));

        assertThat(generated).hasSize(2);
        assertThat(generated).extracting(PedidoItem::getCodigoGerado).doesNotHaveDuplicates();
        assertThat(generated).allSatisfy(item -> assertThat(item.getPaciente()).isSameAs(patient));
        assertThat(product.getEstoqueAtual()).isEqualTo(1);
        assertThat(product.getUltimaSequencia()).isEqualTo(6);
        verify(produtoRepository, times(2)).findByIdForUpdate(8L);
        verify(pedidoItemRepository, times(2)).save(any(PedidoItem.class));
    }

    @Test
    void formatsCodesAndRejectsMissingProductCode() {
        Produto product = product(1L, 0, 0, 0);
        String code = service.montarCodigo(product, 42);
        LocalDate today = LocalDate.now();
        assertThat(code).isEqualTo("1005" + today.getYear() + String.format("%02d", today.getMonthValue()) + "0042");
        assertThatThrownBy(() -> service.montarCodigo(null, 1)).isInstanceOf(BadRequestException.class);
        product.setCodigoProduto("ABC");
        assertThatThrownBy(() -> service.montarCodigo(product, 1)).isInstanceOf(BadRequestException.class);
    }

    private Produto product(Long id, Integer stock, Integer minimum, Integer sequence) {
        Produto product = new Produto();
        product.setId(id);
        product.setNome("Kit");
        product.setCodigoProduto("05");
        product.setCodigoSerie("SERIE-A");
        product.setEstoqueAtual(stock);
        product.setEstoqueMinimo(minimum);
        product.setUltimaSequencia(sequence);
        return product;
    }

    private Produto productInput(String name, String code, String series) {
        Produto product = new Produto();
        product.setNome(name);
        product.setCodigoProduto(code);
        product.setCodigoSerie(series);
        return product;
    }

    private ItemPedidoRequest request(Long productId, Integer quantity) {
        ItemPedidoRequest request = new ItemPedidoRequest();
        request.setProdutoId(productId);
        request.setQuantidade(quantity);
        return request;
    }
}
