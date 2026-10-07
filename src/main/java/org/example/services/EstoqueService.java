package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.models.Estoque;
import org.example.models.MovimentacaoEstoque;
import org.example.models.Usuario;
import org.example.repositories.EstoqueRepository;
import org.example.repositories.MovimentacaoEstoqueRepository;
import org.example.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EstoqueService {

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // Inicializa estoque ao startup
    @PostConstruct
    public void inicializarEstoque() {
        if (estoqueRepository.count() == 0) {
            Estoque estoque = new Estoque();
            estoque.setTotalKits(100); // Valor inicial padrão
            estoqueRepository.save(estoque);
        }
    }

    // Retorna o único registro de estoque
    public Estoque obterEstoque() {
        if (estoqueRepository.count() == 0) {
            Estoque estoque = new Estoque();
            estoque.setTotalKits(100);
            return estoqueRepository.save(estoque);
        }
        return estoqueRepository.findAll().get(0);
    }

    // Retorna total de kits + histórico
    public Map<String, Object> obterEstoqueComHistorico() {
        Estoque estoque = obterEstoque();
        List<MovimentacaoEstoque> historico = movimentacaoRepository.findAllByOrderByDataDesc();

        Map<String, Object> result = new HashMap<>();
        result.put("totalKits", estoque.getTotalKits());
        result.put("historico", historico);
        return result;
    }

    // Registra movimentação (ENTRADA ou SAIDA) de forma atômica
    @Transactional
    public MovimentacaoEstoque registrarMovimentacao(String tipo, Integer quantidade, String motivo) {
        String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase();

        // Validações
        if (!tipoNormalizado.equals("ENTRADA") && !tipoNormalizado.equals("SAIDA")) {
            throw new BadRequestException("Tipo deve ser 'ENTRADA' ou 'SAIDA'");
        }

        if (quantidade == null || quantidade <= 0) {
            throw new BadRequestException("Quantidade deve ser maior que zero");
        }

        Estoque estoque = obterEstoque();
        int saldoAnterior = estoque.getTotalKits() == null ? 0 : estoque.getTotalKits();

        // Validar SAIDA
        if (tipoNormalizado.equals("SAIDA") && quantidade > saldoAnterior) {
            throw new ConflictException("Estoque insuficiente para a saída solicitada");
        }

        // Atualizar estoque
        if (tipoNormalizado.equals("ENTRADA")) {
            estoque.setTotalKits(saldoAnterior + quantidade);
        } else {
            estoque.setTotalKits(saldoAnterior - quantidade);
        }
        estoqueRepository.save(estoque);

        // Registrar movimentação
        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setTipo(tipoNormalizado);
        mov.setQuantidade(quantidade);
        mov.setMotivo(motivo == null ? "" : motivo.trim());
        mov.setSaldoAnterior(saldoAnterior);
        mov.setSaldoPosterior(estoque.getTotalKits());
        mov.setRegistradoEm(LocalDateTime.now());
        usuarioAtual().ifPresent(mov::setUsuario);
        return movimentacaoRepository.save(mov);
    }

    // Verifica se estoque está baixo (<= 5 e > 2)
    public boolean isEstoqueBaixo() {
        Estoque estoque = obterEstoque();
        return estoque.getTotalKits() != null && estoque.getTotalKits() <= 5 && estoque.getTotalKits() > 2;
    }

    // Verifica se estoque está crítico (<= 2)
    public boolean isEstoqueCritico() {
        Estoque estoque = obterEstoque();
        return estoque.getTotalKits() != null && estoque.getTotalKits() <= 2;
    }

    // Decrementa estoque (usado quando cria paciente ativo)
    @Transactional
    public void decrementarEstoque(Integer quantidade) {
        registrarMovimentacao("SAIDA", quantidade, "Retirada para paciente");
    }

    private java.util.Optional<Usuario> usuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return java.util.Optional.empty();
        }

        return usuarioRepository.findByUsername(authentication.getName());
    }
}
