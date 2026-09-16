package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.ItemPedidoRequest;
import org.example.models.PedidoItem;
import org.example.models.Paciente;
import org.example.models.Produto;
import org.example.repositories.PedidoItemRepository;
import org.example.repositories.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PedidoItemRepository pedidoItemRepository;

    public List<Produto> listarTodos() {
        return produtoRepository.findAll();
    }

    public Produto obterPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
    }

    @Transactional
    public Produto criarProduto(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new BadRequestException("Nome do produto é obrigatório");
        }
        if (produto.getCodigoProduto() == null || produto.getCodigoProduto().isBlank()) {
            throw new BadRequestException("Código do produto é obrigatório");
        }
        if (produto.getCodigoSerie() == null || produto.getCodigoSerie().isBlank()) {
            throw new BadRequestException("Código de série é obrigatório");
        }

        produtoRepository.findByCodigoProdutoAndCodigoSerie(produto.getCodigoProduto(), produto.getCodigoSerie())
                .ifPresent(existente -> {
                    throw new ConflictException("Já existe um produto com este código e série");
                });

        if (produto.getEstoqueAtual() == null || produto.getEstoqueAtual() < 0) {
            produto.setEstoqueAtual(0);
        }
        if (produto.getEstoqueMinimo() == null || produto.getEstoqueMinimo() < 0) {
            produto.setEstoqueMinimo(5);
        }
        produto.setUltimaSequencia(0);

        return produtoRepository.save(produto);
    }

    @Transactional
    public Produto atualizarProduto(Long id, Produto atualizado) {
        Produto produto = obterPorId(id);

        if (atualizado.getNome() != null && !atualizado.getNome().isBlank()) {
            produto.setNome(atualizado.getNome());
        }
        if (atualizado.getCodigoProduto() != null && !atualizado.getCodigoProduto().isBlank()) {
            produto.setCodigoProduto(atualizado.getCodigoProduto());
        }
        if (atualizado.getCodigoSerie() != null && !atualizado.getCodigoSerie().isBlank()) {
            produto.setCodigoSerie(atualizado.getCodigoSerie());
        }
        if (atualizado.getEstoqueAtual() != null && atualizado.getEstoqueAtual() >= 0) {
            produto.setEstoqueAtual(atualizado.getEstoqueAtual());
        }
        if (atualizado.getEstoqueMinimo() != null && atualizado.getEstoqueMinimo() >= 0) {
            produto.setEstoqueMinimo(atualizado.getEstoqueMinimo());
        }

        return produtoRepository.save(produto);
    }

    @Transactional
    public void ajustarEstoque(Long id, int delta) {
        Produto produto = produtoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        int novoEstoque = (produto.getEstoqueAtual() == null ? 0 : produto.getEstoqueAtual()) + delta;
        if (novoEstoque < 0) {
            throw new ConflictException("Estoque insuficiente para o produto " + produto.getNome());
        }

        produto.setEstoqueAtual(novoEstoque);
        produtoRepository.save(produto);
    }

    /**
     * Baixa o estoque de cada produto do pedido e gera um código único por unidade vendida.
     * Usa lock pessimista por produto para evitar códigos duplicados em concorrência.
     */
    @Transactional
    public List<PedidoItem> baixarEstoqueEGerarItens(Paciente paciente, List<ItemPedidoRequest> itensSolicitados) {
        List<PedidoItem> gerados = new ArrayList<>();

        for (ItemPedidoRequest itemRequest : itensSolicitados) {
            if (itemRequest.getProdutoId() == null || itemRequest.getQuantidade() == null || itemRequest.getQuantidade() <= 0) {
                throw new BadRequestException("Cada item deve ter produto e quantidade válidos");
            }

            for (int i = 0; i < itemRequest.getQuantidade(); i++) {
                Produto produto = produtoRepository.findByIdForUpdate(itemRequest.getProdutoId())
                        .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

                if (produto.getEstoqueAtual() == null || produto.getEstoqueAtual() <= 0) {
                    throw new ConflictException("Estoque insuficiente para o produto " + produto.getNome());
                }

                produto.setEstoqueAtual(produto.getEstoqueAtual() - 1);

                int proximaSequencia = (produto.getUltimaSequencia() == null ? 0 : produto.getUltimaSequencia()) + 1;
                produto.setUltimaSequencia(proximaSequencia);
                produtoRepository.save(produto);

                PedidoItem pedidoItem = new PedidoItem();
                pedidoItem.setPaciente(paciente);
                pedidoItem.setProduto(produto);
                pedidoItem.setCodigoGerado(montarCodigo(produto, proximaSequencia));
                gerados.add(pedidoItemRepository.save(pedidoItem));
            }
        }

        return gerados;
    }

    public String montarCodigo(Produto produto, int sequencia) {
        if (produto == null) {
            throw new BadRequestException("Produto é obrigatório para gerar o código");
        }

        String codigoProduto = normalizarCodigoNumerico(produto.getCodigoProduto());
        if (codigoProduto.isBlank()) {
            throw new BadRequestException("Código do produto é obrigatório para geração do código de identificação");
        }

        LocalDate hoje = LocalDate.now();
        String mes = String.format("%02d", hoje.getMonthValue());
        String sequenciaFormatada = String.format("%04d", sequencia);
        String codigoProdutoFinal = codigoProduto.substring(Math.max(0, codigoProduto.length() - 3));

        return "1" + leftPad(codigoProdutoFinal, 3, '0')
                + hoje.getYear()
                + mes
                + sequenciaFormatada;
    }

    private String normalizarCodigoNumerico(String valor) {
        if (valor == null) {
            return "";
        }

        String apenasDigitos = valor.replaceAll("\\D+", "");
        return apenasDigitos == null ? "" : apenasDigitos;
    }

    private String leftPad(String valor, int tamanho, char preenchimento) {
        if (valor == null) {
            valor = "";
        }

        StringBuilder builder = new StringBuilder();
        int faltantes = tamanho - valor.length();
        for (int i = 0; i < faltantes; i++) {
            builder.append(preenchimento);
        }
        builder.append(valor);
        return builder.toString();
    }
}
