package org.example.config;

import org.example.models.Paciente;
import org.example.repositories.PacienteRepository;
import org.example.security.PiiCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
@Order(0)
public class PiiDataMigrationRunner implements CommandLineRunner {

    private final PacienteRepository pacienteRepository;
    private final Environment environment;
    private final boolean enabled;

    public PiiDataMigrationRunner(
            PacienteRepository pacienteRepository,
            Environment environment,
            @Value("${app.pii.migration.enabled:true}") boolean enabled) {
        this.pacienteRepository = pacienteRepository;
        this.environment = environment;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        boolean prodActive = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);
        if (prodActive && PiiCrypto.isDefaultKey()) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY real deve ser configurada antes da migração");
        }

        List<Paciente> pacientes = pacienteRepository.findAll();
        if (pacientes.isEmpty()) {
            return;
        }

        boolean hasPlaintext = pacientes.stream().anyMatch(this::hasPlaintextPii);
        if (hasPlaintext) {
            pacienteRepository.saveAll(pacientes);
        }
    }

    private boolean hasPlaintextPii(Paciente paciente) {
        return isPlaintext(paciente.getCpf())
                || isPlaintext(paciente.getTelefone())
                || isPlaintext(paciente.getEmail())
                || isPlaintext(paciente.getEndereco())
                || isPlaintext(paciente.getObservacoes())
                || isPlaintext(paciente.getResultado());
    }

    private boolean isPlaintext(String value) {
        return value != null && !value.isBlank() && !value.startsWith("enc:v1:");
    }
}
