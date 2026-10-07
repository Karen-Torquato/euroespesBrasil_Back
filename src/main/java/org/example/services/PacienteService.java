package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.ItemPedidoRequest;
import org.example.models.Paciente;
import org.example.models.PedidoItem;
import org.example.repositories.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PacienteService {

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private AuditoriaService auditoriaService;

        private static final Set<String> STATUS_FINALIZADOS = new HashSet<>(Set.of(
            "RESULTADO PRONTO"
        ));

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // GET todos os pacientes
    @Transactional
    public List<Paciente> obterTodosPacientes() {
        List<Paciente> pacientes = pacienteRepository.findAll();
        boolean mudou = false;
        for (Paciente paciente : pacientes) {
            if (preencherTimestampsAusentes(paciente)) {
                mudou = true;
            }
            if (normalizarConsistenciaFluxoLegado(paciente)) {
                mudou = true;
            }
            paciente.getItens().size();
        }

        if (mudou) {
            pacienteRepository.saveAll(pacientes);
        }

        return pacientes;
    }

    // GET pacientes com paginação
    @Transactional(readOnly = true)
    public Page<Paciente> listarPaginado(Pageable pageable) {
        Page<Paciente> page = pacienteRepository.findAll(pageable);
        page.getContent().forEach(p -> {
            preencherTimestampsAusentes(p);
            normalizarConsistenciaFluxoLegado(p);
            p.getItens().size();
        });
        return page;
    }

    // GET paciente por ID
    @Transactional
    public Optional<Paciente> obterPacientePorId(Long id) {
        Optional<Paciente> paciente = pacienteRepository.findById(id);
        if (paciente.isPresent()) {
            boolean mudou = preencherTimestampsAusentes(paciente.get());
            if (normalizarConsistenciaFluxoLegado(paciente.get())) {
                mudou = true;
            }
            if (mudou) {
                pacienteRepository.save(paciente.get());
            }
            paciente.get().getItens().size();
        }
        return paciente;
    }

    // POST novo paciente - com validação de estoque
    @Transactional
    public Paciente criarPaciente(Paciente paciente) {
        // Validar nome
        if (paciente.getNome() == null || paciente.getNome().trim().isEmpty()) {
            throw new BadRequestException("Nome é obrigatório");
        }

        validarCpf(paciente.getCpf());
        validarDadosContato(paciente);
        if (paciente.getCodigoIdentificacao() != null && !paciente.getCodigoIdentificacao().isBlank()) {
            paciente.setCodigoIdentificacao(paciente.getCodigoIdentificacao().trim());
            validarCodigoUnicoNovoPaciente(paciente.getCodigoIdentificacao());
        }

        boolean rascunho = isRascunho(paciente.getStatusResultado());
        if (!rascunho) {
            validarCpfObrigatorio(paciente.getCpf());
            validarCodigoIdentificacaoObrigatorio(paciente.getCodigoIdentificacao());
        }

        boolean temItensPedido = paciente.getItensPedido() != null && !paciente.getItensPedido().isEmpty();

        // Se não for rascunho, valida estoque
        if (rascunho) {
            // Rascunho - define statusResultado
            paciente.setStatusResultado("Rascunho");
            Paciente salvo = pacienteRepository.save(paciente);
            auditoriaService.registrar("CRIAR", "PACIENTE", salvo.getId(), "Paciente criado como rascunho");
            salvo.getItens().size();
            return salvo;
        }

        if (paciente.getStatusResultado() == null || paciente.getStatusResultado().isBlank()) {
            paciente.setStatusResultado("Pendente");
        }

        aplicarDatasFluxo(paciente, null);

        if (!temItensPedido) {
            throw new BadRequestException("Selecione ao menos um produto para concluir o cadastro");
        }

        Paciente salvo = salvarComItensEPedido(paciente, paciente.getItensPedido());
        auditoriaService.registrar("CRIAR", "PACIENTE", salvo.getId(), "Paciente criado");
        salvo.getItens().size();
        return salvo;
    }

    // PUT atualiza paciente existente (merge)
    @Transactional
    public Paciente atualizarPaciente(Long id, Paciente pacienteAtualizado) {
        Optional<Paciente> existente = pacienteRepository.findById(id);
        if (existente.isEmpty()) {
            throw new NotFoundException("Paciente não encontrado");
        }

        Paciente paciente = existente.get();
        boolean eraRascunho = isRascunho(paciente.getStatusResultado());
        String statusAnterior = paciente.getStatusResultado();
        String codigoAnterior = paciente.getCodigoIdentificacao();
        List<ItemPedidoRequest> itensPedidoRecebidos = pacienteAtualizado.getItensPedido();

        if (!eraRascunho && isRascunho(pacienteAtualizado.getStatusResultado())) {
            throw new ConflictException("Paciente final não pode voltar para rascunho");
        }

        // Validar se paciente está concluído (bloqueado para edição)
        if (isStatusFinalizado(paciente.getStatusResultado())) {
            throw new ConflictException("Pacientes finalizados não podem ser editados");
        }

        // Merge - só sobrescreve campos não-nulos recebidos
        if (pacienteAtualizado.getNome() != null && !pacienteAtualizado.getNome().isEmpty()) {
            paciente.setNome(pacienteAtualizado.getNome());
        }
        if (pacienteAtualizado.getCpf() != null) {
            validarCpf(pacienteAtualizado.getCpf());
            if (!isRascunho(paciente.getStatusResultado()) && pacienteAtualizado.getCpf().isBlank()) {
                throw new BadRequestException("CPF é obrigatório");
            }
            paciente.setCpf(pacienteAtualizado.getCpf());
        }
        if (pacienteAtualizado.getTelefone() != null) {
            paciente.setTelefone(pacienteAtualizado.getTelefone());
        }
        if (pacienteAtualizado.getEmail() != null) {
            paciente.setEmail(pacienteAtualizado.getEmail());
        }
        if (pacienteAtualizado.getEndereco() != null) {
            paciente.setEndereco(pacienteAtualizado.getEndereco());
        }
        if (pacienteAtualizado.getObservacoes() != null) {
            paciente.setObservacoes(pacienteAtualizado.getObservacoes());
        }
        if (pacienteAtualizado.getQuantidadeKits() != null) {
            paciente.setQuantidadeKits(pacienteAtualizado.getQuantidadeKits());
        }
        if (pacienteAtualizado.getCodigoRastreio() != null) {
            paciente.setCodigoRastreio(pacienteAtualizado.getCodigoRastreio());
        }
        if (pacienteAtualizado.getCodigoIdentificacao() != null) {
            if (pacienteAtualizado.getCodigoIdentificacao().isBlank()) {
                if (!isRascunho(paciente.getStatusResultado())) {
                    throw new BadRequestException("Código de identificação é obrigatório");
                }
            } else {
                pacienteAtualizado.setCodigoIdentificacao(pacienteAtualizado.getCodigoIdentificacao().trim());
                validarCodigoUnicoEdicao(codigoAnterior, pacienteAtualizado.getCodigoIdentificacao(), paciente.getId());
            }
            paciente.setCodigoIdentificacao(pacienteAtualizado.getCodigoIdentificacao());
        }
        if (pacienteAtualizado.getKitEntregueHoje() != null) {
            paciente.setKitEntregueHoje(pacienteAtualizado.getKitEntregueHoje());
        }
        if (pacienteAtualizado.getDataSaidaEstoque() != null) {
            paciente.setDataSaidaEstoque(pacienteAtualizado.getDataSaidaEstoque());
        }
        if (pacienteAtualizado.getDataEntrega() != null) {
            paciente.setDataEntrega(pacienteAtualizado.getDataEntrega());
        }
        if (pacienteAtualizado.getDataPrevisao() != null) {
            paciente.setDataPrevisao(pacienteAtualizado.getDataPrevisao());
        }
        if (pacienteAtualizado.getAtrasado() != null) {
            paciente.setAtrasado(pacienteAtualizado.getAtrasado());
        }
        if (pacienteAtualizado.getDataColetaProcesso() != null) {
            paciente.setDataColetaProcesso(pacienteAtualizado.getDataColetaProcesso());
        }
        if (pacienteAtualizado.getDataColetaRealizada() != null) {
            paciente.setDataColetaRealizada(pacienteAtualizado.getDataColetaRealizada());
        }
        if (pacienteAtualizado.getDataEmMaoBrasil() != null) {
            paciente.setDataEmMaoBrasil(pacienteAtualizado.getDataEmMaoBrasil());
        }
        if (pacienteAtualizado.getDataEnviadoEspanha() != null) {
            paciente.setDataEnviadoEspanha(pacienteAtualizado.getDataEnviadoEspanha());
        }
        if (pacienteAtualizado.getStatusResultado() != null) {
            paciente.setStatusResultado(normalizarStatusEntrada(pacienteAtualizado.getStatusResultado()));
        }
        if (pacienteAtualizado.getResultado() != null) {
            paciente.setResultado(pacienteAtualizado.getResultado());
        }

        validarNaoPularEtapasNoPayload(paciente, pacienteAtualizado);
        validarDadosContato(paciente);

        validarCodigoUnicoEdicao(codigoAnterior, paciente.getCodigoIdentificacao(), paciente.getId());
        aplicarDatasFluxo(paciente, statusAnterior);

        // Ao sair de rascunho, sempre usa produtos do pedido para gerar código no backend.
        if (eraRascunho && !isRascunho(paciente.getStatusResultado())) {
            validarCpfObrigatorio(paciente.getCpf());
            validarCodigoIdentificacaoObrigatorio(paciente.getCodigoIdentificacao());
            if (itensPedidoRecebidos == null || itensPedidoRecebidos.isEmpty()) {
                throw new BadRequestException("Selecione ao menos um produto para concluir o cadastro");
            }
            Paciente salvo = salvarComItensEPedido(paciente, itensPedidoRecebidos);
            auditoriaService.registrar("ALTERAR", "PACIENTE", salvo.getId(), "Dados do paciente alterados");
            salvo.getItens().size();
            return salvo;
        }

        Paciente salvo = pacienteRepository.save(paciente);
        auditoriaService.registrar("ALTERAR", "PACIENTE", salvo.getId(), "Dados do paciente alterados");
        salvo.getItens().size();
        return salvo;
    }

    // DELETE paciente
    @Transactional
    public void deletarPaciente(Long id) {
        Optional<Paciente> paciente = pacienteRepository.findById(id);
        if (paciente.isEmpty()) {
            throw new NotFoundException("Paciente não encontrado");
        }

        // Deletar anexo do disco se existir
        if (paciente.get().getAnexoCaminho() != null && !paciente.get().getAnexoCaminho().isEmpty()) {
            File arquivo = new File(paciente.get().getAnexoCaminho());
            if (arquivo.exists()) {
                arquivo.delete();
            }
        }

        pacienteRepository.deleteById(id);
        auditoriaService.registrar("EXCLUIR", "PACIENTE", id, "Exclusão administrativa");
    }

    @Transactional
    public void anonimizarPaciente(Long id, String motivo) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));

        if (paciente.getAnexoCaminho() != null && !paciente.getAnexoCaminho().isBlank()) {
            File arquivo = new File(paciente.getAnexoCaminho());
            if (arquivo.exists() && !arquivo.delete()) {
                throw new BadRequestException("Não foi possível remover o anexo do paciente");
            }
        }

        paciente.setNome("Paciente anonimizado");
        paciente.setCpf(null);
        paciente.setTelefone(null);
        paciente.setEmail(null);
        paciente.setEndereco(null);
        paciente.setObservacoes(null);
        paciente.setCodigoRastreio(null);
        paciente.setCodigoIdentificacao(null);
        paciente.setKitEntregueHoje(null);
        paciente.setResultado(null);
        paciente.setAnexoNome(null);
        paciente.setAnexoCaminho(null);
        pacienteRepository.save(paciente);
        auditoriaService.registrar("ANONIMIZAR", "PACIENTE", id,
                motivo == null || motivo.isBlank() ? "Solicitação do titular" : motivo.trim());
    }

    @Transactional
    public Map<String, Object> exportarPaciente(Long id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        auditoriaService.registrar("EXPORTAR", "PACIENTE", id, "Exportação de dados do titular");
        Map<String, Object> exportacao = new LinkedHashMap<>();
        exportacao.put("id", paciente.getId());
        exportacao.put("nome", paciente.getNome());
        exportacao.put("cpf", paciente.getCpf());
        exportacao.put("telefone", paciente.getTelefone());
        exportacao.put("email", paciente.getEmail());
        exportacao.put("endereco", paciente.getEndereco());
        exportacao.put("observacoes", paciente.getObservacoes());
        exportacao.put("codigoIdentificacao", paciente.getCodigoIdentificacao());
        exportacao.put("statusResultado", paciente.getStatusResultado());
        exportacao.put("criadoEm", paciente.getCriadoEm());
        exportacao.put("atualizadoEm", paciente.getAtualizadoEm());
        return exportacao;
    }

    // Salvar anexo
    @Transactional
    public void salvarAnexo(Long id, String nomeOriginal, String caminho, String sha256) {
        Optional<Paciente> paciente = pacienteRepository.findById(id);
        if (paciente.isEmpty()) {
            throw new NotFoundException("Paciente não encontrado");
        }

        Paciente p = paciente.get();

        // Deletar arquivo anterior se existir
        if (p.getAnexoCaminho() != null && !p.getAnexoCaminho().isEmpty()) {
            File arquivoAntigo = new File(p.getAnexoCaminho());
            if (arquivoAntigo.exists()) {
                arquivoAntigo.delete();
            }
        }

        p.setAnexoNome(nomeOriginal);
        p.setAnexoCaminho(caminho);
        p.setAnexoSha256(sha256);
        pacienteRepository.save(p);
        auditoriaService.registrar("UPLOAD", "ANEXO", id, "Anexo de paciente atualizado");
    }

    // Obter anexo
    public Optional<Paciente> obterAnexoPaciente(Long id) {
        auditoriaService.registrar("DOWNLOAD", "ANEXO", id, "Download de anexo solicitado");
        return pacienteRepository.findById(id);
    }

    private boolean isRascunho(String statusResultado) {
        return statusResultado != null && "RASCUNHO".equals(normalizarStatus(statusResultado));
    }

    private boolean isStatusFinalizado(String statusResultado) {
        if (statusResultado == null || statusResultado.isBlank()) {
            return false;
        }
        return STATUS_FINALIZADOS.contains(normalizarStatus(statusResultado));
    }

    private String normalizarStatusEntrada(String statusResultado) {
        if (statusResultado == null || statusResultado.isBlank()) {
            return statusResultado;
        }

        String normalizado = normalizarStatus(statusResultado);
        if ("CONCLUIDO".equals(normalizado)) {
            return "Coleta realizada";
        }
        return statusResultado.trim();
    }

    private String normalizarStatus(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }

        return valor
                .trim()
                .replace("ç", "c")
                .replace("Ç", "C")
                .replace("ã", "a")
                .replace("Ã", "A")
                .replace("õ", "o")
                .replace("Õ", "O")
                .replace("á", "a")
                .replace("Á", "A")
                .replace("é", "e")
                .replace("É", "E")
                .replace("í", "i")
                .replace("Í", "I")
                .replace("ó", "o")
                .replace("Ó", "O")
                .replace("ú", "u")
                .replace("Ú", "U")
                .toUpperCase();
    }

    private void validarCodigoUnicoNovoPaciente(String codigoIdentificacao) {
        String codigoNovo = normalizarCodigoManual(codigoIdentificacao);
        if (codigoNovo == null) {
            return;
        }

        boolean existe = pacienteRepository.existsByCodigoIdentificacao(codigoNovo);

        if (existe) {
            throw new ConflictException("Já existe paciente com este código de identificação");
        }
    }

    private void validarCpfObrigatorio(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            throw new BadRequestException("CPF é obrigatório");
        }
    }

    private void validarCodigoIdentificacaoObrigatorio(String codigoIdentificacao) {
        if (codigoIdentificacao == null || codigoIdentificacao.isBlank()) {
            throw new BadRequestException("Código de identificação é obrigatório");
        }
    }

    private void validarCodigoUnicoEdicao(String codigoAnterior, String codigoNovoAtual, Long pacienteId) {
        String anterior = normalizarCodigoManual(codigoAnterior);
        String novo = normalizarCodigoManual(codigoNovoAtual);

        if (novo == null || novo.equals(anterior)) {
            return;
        }

        boolean existe = pacienteRepository.existsByCodigoIdentificacaoAndIdNot(novo, pacienteId);

        if (existe) {
            throw new ConflictException("Já existe paciente com este código de identificação");
        }
    }

    private String normalizarCodigoManual(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String trimmed = codigo.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Paciente salvarComItensEPedido(Paciente paciente, List<ItemPedidoRequest> itensSolicitados) {
        int totalKits = itensSolicitados.stream()
                .mapToInt(item -> item.getQuantidade() == null ? 0 : item.getQuantidade())
                .sum();

        if (totalKits <= 0) {
            throw new BadRequestException("Informe ao menos um produto com quantidade válida");
        }

        paciente.setQuantidadeKits(totalKits);
        paciente.setItensPedido(null);

        Paciente salvo = pacienteRepository.save(paciente);
        List<PedidoItem> itensGerados = produtoService.baixarEstoqueEGerarItens(salvo, itensSolicitados);
        if (itensGerados.isEmpty()) {
            throw new BadRequestException("Selecione ao menos um produto para concluir o cadastro");
        }
        return pacienteRepository.save(salvo);
    }


    private void validarQuantidadeKitsAtivo(Integer quantidadeKits) {
        if (quantidadeKits == null || quantidadeKits <= 0) {
            throw new BadRequestException("Quantidade de kits deve ser maior que zero para paciente final");
        }
    }

    private void validarCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return;
        }

        String digits = cpf.replaceAll("\\D+", "");
        if (digits.length() != 11 || digits.chars().distinct().count() == 1 || !cpfValido(digits)) {
            throw new BadRequestException("CPF inválido");
        }
    }

    private void validarDadosContato(Paciente paciente) {
        if (paciente.getTelefone() != null && !paciente.getTelefone().isBlank()) {
            String telefone = paciente.getTelefone().replaceAll("\\D", "");
            if (telefone.length() < 10 || telefone.length() > 11) {
                throw new BadRequestException("Telefone deve ter 10 ou 11 dígitos");
            }
        }
        if (paciente.getEmail() != null && !paciente.getEmail().isBlank()
                && !paciente.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("E-mail inválido");
        }
    }

    private boolean cpfValido(String digits) {
        int primeiroDigito = calcularDigitoCpf(digits, 9);
        int segundoDigito = calcularDigitoCpf(digits, 10);
        return primeiroDigito == Character.digit(digits.charAt(9), 10)
                && segundoDigito == Character.digit(digits.charAt(10), 10);
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

    private void validarNaoPularEtapasNoPayload(Paciente pacienteMesclado, Paciente payloadAtualizacao) {
        if (!isBlank(payloadAtualizacao.getDataEntrega()) && isBlank(pacienteMesclado.getDataSaidaEstoque())) {
            throw new ConflictException("Conclua a etapa 'Kit saiu do estoque' antes da etapa de entrega");
        }
        if (!isBlank(payloadAtualizacao.getDataColetaProcesso()) && isBlank(pacienteMesclado.getDataEntrega())) {
            throw new ConflictException("Conclua a etapa de entrega antes de 'Coleta em processo'");
        }
        if (!isBlank(payloadAtualizacao.getDataColetaRealizada()) && isBlank(pacienteMesclado.getDataColetaProcesso())) {
            throw new ConflictException("Conclua 'Coleta em processo' antes de 'Coleta realizada'");
        }
        if (!isBlank(payloadAtualizacao.getDataEmMaoBrasil()) && isBlank(pacienteMesclado.getDataColetaRealizada())) {
            throw new ConflictException("Conclua 'Coleta realizada' antes de 'Em mão Euroespes Brasil'");
        }
        if (!isBlank(payloadAtualizacao.getDataEnviadoEspanha()) && isBlank(pacienteMesclado.getDataEmMaoBrasil())) {
            throw new ConflictException("Conclua 'Em mão Euroespes Brasil' antes de 'Enviado para Euroespes Espanha'");
        }
    }

    private void aplicarDatasFluxo(Paciente paciente, String statusAnterior) {
        String statusAtual = normalizarStatusEntrada(paciente.getStatusResultado());
        if (statusAtual != null) {
            paciente.setStatusResultado(statusAtual);
        }

        boolean rascunho = isRascunho(paciente.getStatusResultado());
        if (rascunho) {
            return;
        }

        boolean criandoAtivo = statusAnterior == null;
        boolean ativandoRascunho = isRascunho(statusAnterior) && !rascunho;

        if ((criandoAtivo || ativandoRascunho) && isBlank(paciente.getDataSaidaEstoque())) {
            paciente.setDataSaidaEstoque(nowAsString());
        }

        // Se uma etapa volta para pendente, todas as posteriores voltam para pendente.
        if (isBlank(paciente.getDataSaidaEstoque())) {
            paciente.setDataEntrega(null);
            paciente.setDataColetaProcesso(null);
            paciente.setDataColetaRealizada(null);
            paciente.setDataEmMaoBrasil(null);
            paciente.setDataEnviadoEspanha(null);
        } else if (isBlank(paciente.getDataEntrega())) {
            paciente.setDataColetaProcesso(null);
            paciente.setDataColetaRealizada(null);
            paciente.setDataEmMaoBrasil(null);
            paciente.setDataEnviadoEspanha(null);
        } else if (isBlank(paciente.getDataColetaProcesso())) {
            paciente.setDataColetaRealizada(null);
            paciente.setDataEmMaoBrasil(null);
            paciente.setDataEnviadoEspanha(null);
        } else if (isBlank(paciente.getDataColetaRealizada())) {
            paciente.setDataEmMaoBrasil(null);
            paciente.setDataEnviadoEspanha(null);
        } else if (isBlank(paciente.getDataEmMaoBrasil())) {
            paciente.setDataEnviadoEspanha(null);
        }

        // Regras de progressão sequencial.
        if (!isBlank(paciente.getDataEntrega()) && isBlank(paciente.getDataSaidaEstoque())) {
            throw new ConflictException("Conclua a etapa 'Kit saiu do estoque' antes da etapa de entrega");
        }
        if (!isBlank(paciente.getDataColetaProcesso()) && isBlank(paciente.getDataEntrega())) {
            throw new ConflictException("Conclua a etapa de entrega antes de 'Coleta em processo'");
        }
        if (!isBlank(paciente.getDataColetaRealizada()) && isBlank(paciente.getDataColetaProcesso())) {
            throw new ConflictException("Conclua 'Coleta em processo' antes de 'Coleta realizada'");
        }
        if (!isBlank(paciente.getDataEmMaoBrasil()) && isBlank(paciente.getDataColetaRealizada())) {
            throw new ConflictException("Conclua 'Coleta realizada' antes de 'Em mão Euroespes Brasil'");
        }
        if (!isBlank(paciente.getDataEnviadoEspanha()) && isBlank(paciente.getDataEmMaoBrasil())) {
            throw new ConflictException("Conclua 'Em mão Euroespes Brasil' antes de 'Enviado para Euroespes Espanha'");
        }
        if (!isBlank(paciente.getDataEnviadoEspanha()) && isBlank(paciente.getCodigoRastreio())) {
            throw new BadRequestException("Código de rastreio é obrigatório para concluir o envio para Euroespes Espanha");
        }

        paciente.setKitEntregueHoje(isBlank(paciente.getDataEntrega()) ? "nao" : "sim");

        if ("RESULTADO PRONTO".equals(normalizarStatus(paciente.getStatusResultado()))) {
            return;
        }

        if (!isBlank(paciente.getDataEnviadoEspanha())) {
            paciente.setStatusResultado("Enviado para Euroespes Espanha");
            return;
        }

        if (!isBlank(paciente.getDataEmMaoBrasil())) {
            paciente.setStatusResultado("Em mão Euroespes Brasil");
            return;
        }

        // Status principal sempre deriva da última etapa concluída do fluxo principal.
        if (!isBlank(paciente.getDataColetaRealizada())) {
            paciente.setStatusResultado("Coleta realizada");
            return;
        }

        if (!isBlank(paciente.getDataColetaProcesso())) {
            paciente.setStatusResultado("Coleta em processo");
            return;
        }

        paciente.setStatusResultado("Pendente");
    }

    private String nowAsString() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean preencherTimestampsAusentes(Paciente paciente) {
        boolean mudou = false;
        LocalDateTime criadoEm = paciente.getCriadoEm();
        LocalDateTime atualizadoEm = paciente.getAtualizadoEm();

        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
            paciente.setCriadoEm(criadoEm);
            mudou = true;
        }

        if (atualizadoEm == null || atualizadoEm.isBefore(criadoEm)) {
            paciente.setAtualizadoEm(criadoEm);
            mudou = true;
        }

        return mudou;
    }

    private boolean normalizarConsistenciaFluxoLegado(Paciente paciente) {
        boolean mudou = false;

        if (isRascunho(paciente.getStatusResultado())) {
            return false;
        }

        if (isBlank(paciente.getDataSaidaEstoque())) {
            mudou |= limparDataEntregaELaterais(paciente);
        } else if (isBlank(paciente.getDataEntrega())) {
            mudou |= limparDataColetaELaterais(paciente);
        } else if (isBlank(paciente.getDataColetaProcesso())) {
            mudou |= limparDataColetaRealizadaELaterais(paciente);
        } else if (isBlank(paciente.getDataColetaRealizada())) {
            if (!isBlank(paciente.getDataEmMaoBrasil())) {
                paciente.setDataEmMaoBrasil(null);
                mudou = true;
            }
            if (!isBlank(paciente.getDataEnviadoEspanha())) {
                paciente.setDataEnviadoEspanha(null);
                mudou = true;
            }
        } else if (isBlank(paciente.getDataEmMaoBrasil())) {
            if (!isBlank(paciente.getDataEnviadoEspanha())) {
                paciente.setDataEnviadoEspanha(null);
                mudou = true;
            }
        }

        String kitEntregueEsperado = isBlank(paciente.getDataEntrega()) ? "nao" : "sim";
        if (!Objects.equals(kitEntregueEsperado, paciente.getKitEntregueHoje())) {
            paciente.setKitEntregueHoje(kitEntregueEsperado);
            mudou = true;
        }

        String statusNormalizado = normalizarStatus(paciente.getStatusResultado());
        String statusEsperado;
        if ("RESULTADO PRONTO".equals(statusNormalizado)) {
            statusEsperado = "Resultado pronto";
        } else if (!isBlank(paciente.getDataEnviadoEspanha())) {
            statusEsperado = "Enviado para Euroespes Espanha";
        } else if (!isBlank(paciente.getDataEmMaoBrasil())) {
            statusEsperado = "Em mão Euroespes Brasil";
        } else if (!isBlank(paciente.getDataColetaRealizada())) {
            statusEsperado = "Coleta realizada";
        } else if (!isBlank(paciente.getDataColetaProcesso())) {
            statusEsperado = "Coleta em processo";
        } else {
            statusEsperado = "Pendente";
        }

        if (!Objects.equals(statusEsperado, paciente.getStatusResultado())) {
            paciente.setStatusResultado(statusEsperado);
            mudou = true;
        }

        return mudou;
    }

    private boolean limparDataEntregaELaterais(Paciente paciente) {
        boolean mudou = false;
        if (!isBlank(paciente.getDataEntrega())) {
            paciente.setDataEntrega(null);
            mudou = true;
        }
        mudou |= limparDataColetaELaterais(paciente);
        return mudou;
    }

    private boolean limparDataColetaELaterais(Paciente paciente) {
        boolean mudou = false;
        if (!isBlank(paciente.getDataColetaProcesso())) {
            paciente.setDataColetaProcesso(null);
            mudou = true;
        }
        mudou |= limparDataColetaRealizadaELaterais(paciente);
        return mudou;
    }

    private boolean limparDataColetaRealizadaELaterais(Paciente paciente) {
        boolean mudou = false;
        if (!isBlank(paciente.getDataColetaRealizada())) {
            paciente.setDataColetaRealizada(null);
            mudou = true;
        }
        if (!isBlank(paciente.getDataEmMaoBrasil())) {
            paciente.setDataEmMaoBrasil(null);
            mudou = true;
        }
        if (!isBlank(paciente.getDataEnviadoEspanha())) {
            paciente.setDataEnviadoEspanha(null);
            mudou = true;
        }
        return mudou;
    }
}
