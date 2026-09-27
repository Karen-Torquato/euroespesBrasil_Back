package org.example.services;

import org.example.models.AuditoriaEvento;
import org.example.repositories.AuditoriaEventoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {

    private final AuditoriaEventoRepository repository;

    public AuditoriaService(AuditoriaEventoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(String acao, String recurso, Long recursoId, String motivo) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String ator = authentication == null || authentication.getName() == null
                ? "sistema"
                : authentication.getName();
        repository.save(new AuditoriaEvento(acao, recurso, recursoId, ator, motivo));
    }
}
