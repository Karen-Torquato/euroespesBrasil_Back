package org.example.services;

import org.example.models.AuditoriaEvento;
import org.example.repositories.AuditoriaEventoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AuditoriaService — rastreabilidade de ações")
class AuditoriaServiceTest {

    @AfterEach
    void limparContextoSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("deve registrar ator autenticado")
    void registraAtorAutenticado() {
        AuditoriaEventoRepository repository = mock(AuditoriaEventoRepository.class);
        AuditoriaService service = new AuditoriaService(repository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a")
        );

        service.registrar("EXPORTAR", "PACIENTE", 7L, "Exportação LGPD");

        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        AuditoriaEvento evento = captor.getValue();
        assertThat(evento.getAcao()).isEqualTo("EXPORTAR");
        assertThat(evento.getRecurso()).isEqualTo("PACIENTE");
        assertThat(evento.getRecursoId()).isEqualTo(7L);
        assertThat(evento.getAtor()).isEqualTo("admin");
        assertThat(evento.getMotivo()).isEqualTo("Exportação LGPD");
        assertThat(evento.getOcorridoEm()).isNotNull();
    }

    @Test
    @DisplayName("deve usar ator sistema quando não houver autenticação")
    void usaAtorSistemaSemAutenticacao() {
        AuditoriaEventoRepository repository = mock(AuditoriaEventoRepository.class);
        AuditoriaService service = new AuditoriaService(repository);

        service.registrar("ANONIMIZAR", "PACIENTE", 11L, "Solicitação do titular");

        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAtor()).isEqualTo("sistema");
    }
}
