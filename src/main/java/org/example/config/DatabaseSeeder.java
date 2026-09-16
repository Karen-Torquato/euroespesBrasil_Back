package org.example.config;

import org.example.models.Estoque;
import org.example.models.MovimentacaoEstoque;
import org.example.models.Paciente;
import org.example.models.Usuario;
import org.example.repositories.EstoqueRepository;
import org.example.repositories.MovimentacaoEstoqueRepository;
import org.example.repositories.PacienteRepository;
import org.example.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final String SEED_PREFIX = "9 900 2026 07 - ";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final PacienteRepository pacienteRepository;
    private final EstoqueRepository estoqueRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${app.seed.create-default-admin:true}")
    private boolean createDefaultAdmin;

    public DatabaseSeeder(
            PacienteRepository pacienteRepository,
            EstoqueRepository estoqueRepository,
            MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.pacienteRepository = pacienteRepository;
        this.estoqueRepository = estoqueRepository;
        this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Cria usuário admin no primeiro boot (senha hasheada com BCrypt)
        if (createDefaultAdmin && usuarioRepository.count() == 0) {
            Usuario admin = new Usuario();
            admin.setUsername(adminUsername);
            admin.setSenha(passwordEncoder.encode(adminPassword));
            admin.setRole("ROLE_ADMIN");
            usuarioRepository.save(admin);
        }

        // Massa de dados fake deve existir apenas em ambientes não produtivos.
        if (!seedEnabled) {
            return;
        }

        criarUsuarioSeNaoExistir("medico", "Medico@12345", "ROLE_MEDICO");
        criarUsuarioSeNaoExistir("recepcao", "Recepcao@12345", "ROLE_RECEPCAO");
        criarUsuarioSeNaoExistir("leitura", "Leitura@12345", "ROLE_LEITURA");

        boolean jaSeedado = pacienteRepository.findAll().stream()
                .map(Paciente::getCodigoIdentificacao)
                .anyMatch(codigo -> codigo != null && codigo.startsWith(SEED_PREFIX));

        if (jaSeedado) {
            atualizarMassaSeedExistente();
            return;
        }

        List<Paciente> massa = new ArrayList<>();
        int seq = 1;

        // 5 pacientes em rascunho
        for (int i = 1; i <= 5; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Rascunho " + i,
                    "Rascunho",
                    "",
                    1,
                    "(11) 90000-10" + i,
                    "",
                    "Rascunho de coleta " + i
            ));
        }

        // 2 em etapa 1 (kit saiu do estoque / pendente)
        for (int i = 1; i <= 2; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Etapa1 Paciente " + i,
                    "Pendente",
                    "nao",
                    1,
                    "(11) 91000-20" + i,
                    "",
                    "Aguardando entrega do kit"
            ));
        }

        // 2 em etapa 2 (kit entregue)
        for (int i = 1; i <= 2; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Etapa2 Paciente " + i,
                    "Pendente",
                    "sim",
                    1,
                    "(11) 92000-30" + i,
                    "AA1234567" + (80 + i) + "BR",
                    "Kit entregue ao paciente"
            ));
        }

        // 2 em etapa 3 (coleta em processo)
        for (int i = 1; i <= 2; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Etapa3 Paciente " + i,
                    "Coleta em processo",
                    "sim",
                    1,
                    "(11) 93000-40" + i,
                    "AA1234567" + (60 + i) + "BR",
                    "Coleta em andamento"
            ));
        }

        // 10 concluídos (coleta realizada)
        for (int i = 1; i <= 10; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Concluído Paciente " + i,
                    "Coleta realizada",
                    "sim",
                    1,
                    "(11) 94000-50" + (i < 10 ? "0" + i : String.valueOf(i)),
                    "AA1234567" + (30 + i) + "BR",
                    "Coleta finalizada"
            ));
        }

        // 2 em mão Brasil
        for (int i = 1; i <= 2; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Brasil Paciente " + i,
                    "Em mão Euroespes Brasil",
                    "sim",
                    1,
                    "(11) 95000-60" + i,
                    "AA1234567" + (10 + i) + "BR",
                    "Material recebido no Brasil"
            ));
        }

        // 2 enviados para Espanha
        for (int i = 1; i <= 2; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Espanha Paciente " + i,
                    "Enviado para Euroespes Espanha",
                    "sim",
                    1,
                    "(11) 96000-70" + i,
                    "AA1234567" + i + "BR",
                    "Enviado para processamento externo"
            ));
        }

        // 2 com resultado pronto
        for (int i = 1; i <= 2; i++) {
            massa.add(buildPaciente(
                    seq++,
                    "Resultado Paciente " + i,
                    "Resultado pronto",
                    "sim",
                    1,
                    "(11) 97000-80" + i,
                    "AA2234567" + i + "BR",
                    "Resultado pronto para consulta"
            ));
        }

        pacienteRepository.saveAll(massa);

        Estoque estoque = estoqueRepository.findAll().stream().findFirst().orElseGet(Estoque::new);
        estoque.setTotalKits(54);
        estoqueRepository.save(estoque);

        if (movimentacaoEstoqueRepository.count() == 0) {
            movimentacaoEstoqueRepository.save(buildMov("ENTRADA", 100, "Carga inicial de estoque"));
            movimentacaoEstoqueRepository.save(buildMov("SAIDA", 46, "Alocação inicial para pacientes seed"));
        }
    }

    private void atualizarMassaSeedExistente() {
        List<Paciente> seedados = pacienteRepository.findAll().stream()
                .filter(p -> p.getCodigoIdentificacao() != null && p.getCodigoIdentificacao().startsWith(SEED_PREFIX))
                .toList();

        for (Paciente paciente : seedados) {
            if (paciente.getDataSaidaEstoque() != null
                    && paciente.getDataEntrega() != null
                    && paciente.getDataColetaProcesso() != null
                    && paciente.getDataColetaRealizada() != null) {
                continue;
            }

            int sequence = extrairSequencia(paciente.getCodigoIdentificacao());
            aplicarDatasFluxoSeed(paciente, paciente.getStatusResultado(), sequence);
        }

        pacienteRepository.saveAll(seedados);
    }

    private Paciente buildPaciente(
            int sequence,
            String nome,
            String status,
            String kitEntregueHoje,
            int quantidadeKits,
            String telefone,
            String codigoRastreio,
            String observacoes
    ) {
        Paciente p = new Paciente();
        p.setNome(nome);
        p.setStatusResultado(status);
        p.setKitEntregueHoje(kitEntregueHoje);
        p.setQuantidadeKits(quantidadeKits);
        p.setTelefone(telefone);
        p.setCodigoRastreio(codigoRastreio);
        p.setObservacoes(observacoes);
        p.setCodigoIdentificacao(formatCodigo(sequence));
        p.setDataPrevisao("2026-07-20");
        aplicarDatasFluxoSeed(p, status, sequence);
        return p;
    }

    private void aplicarDatasFluxoSeed(Paciente paciente, String status, int sequence) {
        LocalDateTime base = LocalDateTime.of(2026, 6, 25, 8, 59, 16).plusHours(sequence);

        paciente.setDataSaidaEstoque(format(base));

        if (!"Rascunho".equalsIgnoreCase(status)) {
            paciente.setDataEntrega(format(base));
        }

        if ("Coleta em processo".equalsIgnoreCase(status)
                || "Coleta realizada".equalsIgnoreCase(status)
                || "Em mão Euroespes Brasil".equalsIgnoreCase(status)
                || "Enviado para Euroespes Espanha".equalsIgnoreCase(status)
                || "Resultado pronto".equalsIgnoreCase(status)) {
            paciente.setDataColetaProcesso(format(base.plusDays(6)));
        }

        if ("Coleta realizada".equalsIgnoreCase(status)
                || "Em mão Euroespes Brasil".equalsIgnoreCase(status)
                || "Enviado para Euroespes Espanha".equalsIgnoreCase(status)
                || "Resultado pronto".equalsIgnoreCase(status)) {
            paciente.setDataColetaRealizada(format(base.plusDays(7)));
        }

        if ("Em mão Euroespes Brasil".equalsIgnoreCase(status)
                || "Enviado para Euroespes Espanha".equalsIgnoreCase(status)
                || "Resultado pronto".equalsIgnoreCase(status)) {
            paciente.setDataEmMaoBrasil(format(base.plusDays(8)));
        }

        if ("Enviado para Euroespes Espanha".equalsIgnoreCase(status)
                || "Resultado pronto".equalsIgnoreCase(status)) {
            paciente.setDataEnviadoEspanha(format(base.plusDays(9)));
        }
    }

    private String format(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    private String formatCodigo(int sequence) {
        return SEED_PREFIX + String.format("%04d", sequence);
    }

    private int extrairSequencia(String codigoIdentificacao) {
        if (codigoIdentificacao == null || codigoIdentificacao.length() < 4) {
            return 1;
        }
        String digits = codigoIdentificacao.replaceAll("\\D+", "");
        if (digits.length() < 4) {
            return 1;
        }
        try {
            return Integer.parseInt(digits.substring(digits.length() - 4));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private MovimentacaoEstoque buildMov(String tipo, int quantidade, String motivo) {
        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setTipo(tipo);
        mov.setQuantidade(quantidade);
        mov.setMotivo(motivo);
        return mov;
    }

    private void criarUsuarioSeNaoExistir(String username, String senha, String role) {
        if (usuarioRepository.findByUsername(username).isPresent()) {
            return;
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setRole(role);
        usuarioRepository.save(usuario);
    }
}
