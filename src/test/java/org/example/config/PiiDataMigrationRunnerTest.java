package org.example.config;

import org.example.models.Paciente;
import org.example.repositories.PacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PiiDataMigrationRunner — migração de dados pessoais")
class PiiDataMigrationRunnerTest {

    @AfterEach
    void limparChavePii() {
        System.clearProperty("PII_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("não deve executar quando migration estiver desabilitada")
    void naoExecutaQuandoDesabilitada() throws Exception {
        PacienteRepository repository = Mockito.mock(PacienteRepository.class);
        MockEnvironment environment = new MockEnvironment();
        PiiDataMigrationRunner runner = new PiiDataMigrationRunner(repository, environment, false);

        runner.run();

        verify(repository, never()).findAll();
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("deve falhar em prod com chave default")
    void falhaEmProdComChaveDefault() {
        PacienteRepository repository = Mockito.mock(PacienteRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PiiDataMigrationRunner runner = new PiiDataMigrationRunner(repository, environment, true);

        assertThatThrownBy(runner::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PII_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("deve salvar quando encontrar dados em texto puro")
    void salvaQuandoEncontrarTextoPuro() throws Exception {
        PacienteRepository repository = Mockito.mock(PacienteRepository.class);
        MockEnvironment environment = new MockEnvironment();
        PiiDataMigrationRunner runner = new PiiDataMigrationRunner(repository, environment, true);

        Paciente paciente = new Paciente();
        paciente.setNome("Paciente");
        paciente.setCpf("52998224725");
        when(repository.findAll()).thenReturn(List.of(paciente));

        runner.run();

        verify(repository).saveAll(anyList());
    }

    @Test
    @DisplayName("não deve salvar quando todos os dados já estiverem cifrados")
    void naoSalvaQuandoJaCifrado() throws Exception {
        PacienteRepository repository = Mockito.mock(PacienteRepository.class);
        MockEnvironment environment = new MockEnvironment();
        PiiDataMigrationRunner runner = new PiiDataMigrationRunner(repository, environment, true);

        Paciente paciente = new Paciente();
        paciente.setNome("Paciente");
        paciente.setCpf("enc:v1:abc");
        paciente.setEmail("enc:v1:def");
        when(repository.findAll()).thenReturn(List.of(paciente));

        runner.run();

        verify(repository, never()).saveAll(anyList());
    }
}
