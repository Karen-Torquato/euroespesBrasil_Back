package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.ItemPedidoRequest;
import org.example.models.MovimentacaoEstoque;
import org.example.models.PedidoItem;
import org.example.models.Paciente;
import org.example.models.Produto;
import org.example.models.Usuario;
import org.example.repositories.MovimentacaoEstoqueRepository;
import org.example.repositories.PedidoItemRepository;
import org.example.repositories.ProdutoRepository;
import org.example.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PedidoItemRepository pedidoItemRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public List<Produto> listarTodos() {
        return produtoRepository.findAll();
    }

    public List<Produto> listarProdutosEmEstoqueBaixo() {
        return produtoRepository.findProdutosEmEstoqueBaixo();
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

        int saldoAnterior = produto.getEstoqueAtual() == null ? 0 : produto.getEstoqueAtual();
        int novoEstoque = saldoAnterior + delta;
        if (novoEstoque < 0) {
            throw new ConflictException("Estoque insuficiente para o produto " + produto.getNome());
        }

        produto.setEstoqueAtual(novoEstoque);
        produtoRepository.save(produto);

        if (delta != 0) {
            MovimentacaoEstoque mov = new MovimentacaoEstoque();
            mov.setProduto(produto);
            mov.setTipo(delta > 0 ? "ENTRADA" : "SAIDA");
            mov.setQuantidade(Math.abs(delta));
            mov.setMotivo("Ajuste manual no cadastro de produtos");
            mov.setSaldoAnterior(saldoAnterior);
            mov.setSaldoPosterior(novoEstoque);
            mov.setRegistradoEm(LocalDateTime.now());
            usuarioAtual().ifPresent(mov::setUsuario);
            if (movimentacaoEstoqueRepository != null) {
                movimentacaoEstoqueRepository.save(mov);
            }
        }
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

            Integer saldoInicialItem = null;
            Produto produtoMovimentacao = null;

            for (int i = 0; i < itemRequest.getQuantidade(); i++) {
                Produto produto = produtoRepository.findByIdForUpdate(itemRequest.getProdutoId())
                        .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

                if (produto.getEstoqueAtual() == null || produto.getEstoqueAtual() <= 0) {
                    throw new ConflictException("Estoque insuficiente para o produto " + produto.getNome());
                }

                if (saldoInicialItem == null) {
                    saldoInicialItem = produto.getEstoqueAtual();
                    produtoMovimentacao = produto;
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

            if (saldoInicialItem != null && produtoMovimentacao != null && movimentacaoEstoqueRepository != null) {
                int saldoFinalItem = saldoInicialItem - itemRequest.getQuantidade();
                java.util.Optional<Usuario> usuarioLogado = usuarioAtual();

                MovimentacaoEstoque mov = new MovimentacaoEstoque();
                mov.setProduto(produtoMovimentacao);
                mov.setTipo("SAIDA");
                mov.setQuantidade(itemRequest.getQuantidade());
                mov.setMotivo(montarMotivoInclusaoPaciente(paciente, usuarioLogado.orElse(null)));
                mov.setSaldoAnterior(saldoInicialItem);
                mov.setSaldoPosterior(saldoFinalItem);
                mov.setRegistradoEm(LocalDateTime.now());
                usuarioLogado.ifPresent(mov::setUsuario);
                movimentacaoEstoqueRepository.save(mov);
            }
        }

        return gerados;
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoProdutoResumo> listarAuditoriaPorProduto(Long produtoId) {
        obterPorId(produtoId);
        return movimentacaoEstoqueRepository.findAuditoriaByProdutoId(produtoId).stream()
                .map(item -> new MovimentacaoProdutoResumo(
                        item.getId(),
                        item.getTipo(),
                        item.getQuantidade(),
                        item.getMotivo(),
                        item.getSaldoAnterior(),
                        item.getSaldoPosterior(),
                        item.getRegistradoEm() != null ? item.getRegistradoEm() : item.getData(),
                        item.getUsuario() == null ? "sistema" : item.getUsuario().getUsername()
                ))
                .toList();
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

    private java.util.Optional<Usuario> usuarioAtual() {
        if (usuarioRepository == null) {
            return java.util.Optional.empty();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return java.util.Optional.empty();
        }
        return usuarioRepository.findByUsername(authentication.getName());
    }

    private String montarMotivoInclusaoPaciente(Paciente paciente, Usuario usuarioLogado) {
        String nomePaciente = paciente == null || paciente.getNome() == null || paciente.getNome().isBlank()
                ? "(sem nome)"
                : paciente.getNome().trim();

        if (usuarioLogado != null
                && "ROLE_MEDICO".equalsIgnoreCase(usuarioLogado.getRole())
                && usuarioLogado.getUsername() != null
                && !usuarioLogado.getUsername().isBlank()) {
            return "Incluso no paciente " + nomePaciente + " através do médico " + usuarioLogado.getUsername().trim();
        }

        return "Incluso no paciente " + nomePaciente;
    }

    public record MovimentacaoProdutoResumo(
            Long id,
            String tipo,
            Integer quantidade,
            String motivo,
            Integer saldoAnterior,
            Integer saldoPosterior,
            LocalDateTime registradoEm,
            String usuario
    ) {}
}
