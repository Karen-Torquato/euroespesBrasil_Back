package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.Medico;
import org.example.models.MedicoEstoque;
import org.example.models.Produto;
import org.example.repositories.MedicoEstoqueRepository;
import org.example.repositories.MedicoRepository;
import org.example.repositories.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MedicoService {

    private final MedicoRepository medicoRepository;
    private final MedicoEstoqueRepository medicoEstoqueRepository;
    private final ProdutoRepository produtoRepository;

    public MedicoService(MedicoRepository medicoRepository,
                         MedicoEstoqueRepository medicoEstoqueRepository,
                         ProdutoRepository produtoRepository) {
        this.medicoRepository = medicoRepository;
        this.medicoEstoqueRepository = medicoEstoqueRepository;
        this.produtoRepository = produtoRepository;
    }

    public List<Medico> listarTodos() {
        return medicoRepository.findAllByOrderByNomeAsc();
    }

    public Medico obterPorId(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Médico não encontrado"));
    }

    @Transactional
    public Medico criar(Medico medico) {
        validar(medico);
        normalizarCampos(medico);
        medico.setId(null);
        return medicoRepository.save(medico);
    }

    @Transactional
    public Medico atualizar(Long id, Medico medico) {
        Medico existente = obterPorId(id);
        validar(medico);
        normalizarCampos(medico);

        existente.setNome(medico.getNome());
        existente.setCpf(medico.getCpf());
        existente.setCrm(medico.getCrm());
        existente.setEmail(medico.getEmail());
        existente.setTelefone(medico.getTelefone());
        existente.setEspecialidade(medico.getEspecialidade());
        existente.setEstoquePrivado(medico.isEstoquePrivado());
        existente.setAtivo(medico.isAtivo());
        return medicoRepository.save(existente);
    }

    @Transactional
    public void remover(Long id) {
        Medico medico = obterPorId(id);
        medico.setAtivo(false);
        medicoRepository.save(medico);
    }

    @Transactional(readOnly = true)
    public List<MedicoEstoqueResumo> estoqueDoMedico(Long medicoId) {
        obterPorId(medicoId);
        return medicoEstoqueRepository.findResumoByMedicoId(medicoId).stream()
                .map(item -> new MedicoEstoqueResumo(
                        item.getId(),
                        item.getProdutoId(),
                        item.getProdutoNome(),
                        item.getQuantidadeAtual(),
                        item.getEstoqueMinimo()))
                .toList();
    }

    @Transactional
    public MedicoEstoqueResumo ajustarEstoque(Long medicoId, Long produtoId, int delta, Integer estoqueMinimo) {
        if (delta == 0) {
            throw new BadRequestException("Delta precisa ser diferente de zero");
        }
        Medico medico = obterPorId(medicoId);
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        MedicoEstoque estoque = medicoEstoqueRepository.findAllByMedico_Id(medicoId).stream()
                .filter(item -> item.getProduto() != null && produtoId.equals(item.getProduto().getId()))
                .findFirst()
                .orElseGet(() -> {
                    MedicoEstoque novo = new MedicoEstoque();
                    novo.setMedico(medico);
                    novo.setProduto(produto);
                    novo.setQuantidadeAtual(0);
                    novo.setEstoqueMinimo(estoqueMinimo == null ? 0 : Math.max(0, estoqueMinimo));
                    return novo;
                });

        int saldoAtual = estoque.getQuantidadeAtual() == null ? 0 : estoque.getQuantidadeAtual();
        int novoSaldo = saldoAtual + delta;
        if (novoSaldo < 0) {
            throw new ConflictException("Estoque insuficiente para o médico " + medico.getNome());
        }

        estoque.setQuantidadeAtual(novoSaldo);
        if (estoqueMinimo != null && estoqueMinimo >= 0) {
            estoque.setEstoqueMinimo(estoqueMinimo);
        }
        MedicoEstoque salvo = medicoEstoqueRepository.save(estoque);
        return new MedicoEstoqueResumo(
                salvo.getId(),
                salvo.getProduto() == null ? null : salvo.getProduto().getId(),
                salvo.getProduto() == null ? null : salvo.getProduto().getNome(),
                salvo.getQuantidadeAtual(),
                salvo.getEstoqueMinimo()
        );
    }

    private void validar(Medico medico) {
        if (medico == null) {
            throw new BadRequestException("Médico é obrigatório");
        }
        if (medico.getNome() == null || medico.getNome().isBlank()) {
            throw new BadRequestException("Nome do médico é obrigatório");
        }
        if (medico.getCpf() == null || medico.getCpf().isBlank()) {
            throw new BadRequestException("CPF do médico é obrigatório");
        }
        if (!cpfValido(medico.getCpf())) {
            throw new BadRequestException("CPF do médico inválido");
        }
        if (medico.getCrm() == null || medico.getCrm().isBlank()) {
            throw new BadRequestException("CRM do médico é obrigatório");
        }
        if (medico.getEspecialidade() == null || medico.getEspecialidade().isBlank()) {
            throw new BadRequestException("Especialidade do médico é obrigatória");
        }
    }

    private void normalizarCampos(Medico medico) {
        medico.setNome(medico.getNome() == null ? null : medico.getNome().trim());
        medico.setCpf(medico.getCpf() == null ? null : medico.getCpf().trim());
        medico.setCrm(medico.getCrm() == null ? null : medico.getCrm().trim());
        medico.setEspecialidade(medico.getEspecialidade() == null ? null : medico.getEspecialidade().trim());
        medico.setEmail(medico.getEmail() == null ? null : medico.getEmail().trim());
        medico.setTelefone(medico.getTelefone() == null ? null : medico.getTelefone().trim());
    }

    private boolean cpfValido(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return false;
        }

        String digits = cpf.replaceAll("\\D+", "");
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }

        int d1 = calcularDigitoCpf(digits, 9);
        int d2 = calcularDigitoCpf(digits, 10);
        return d1 == Character.digit(digits.charAt(9), 10)
                && d2 == Character.digit(digits.charAt(10), 10);
    }

    private int calcularDigitoCpf(String digits, int tamanhoBase) {
        int soma = 0;
        int peso = tamanhoBase + 1;
        for (int indice = 0; indice < tamanhoBase; indice++) {
            soma += Character.digit(digits.charAt(indice), 10) * peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    public record MedicoEstoqueResumo(Long id, Long produtoId, String produtoNome, Integer quantidadeAtual, Integer estoqueMinimo) {}
}
