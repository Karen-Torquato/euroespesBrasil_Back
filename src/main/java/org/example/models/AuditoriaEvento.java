package org.example.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_eventos", indexes = {
        @Index(name = "idx_auditoria_ocorrido_em", columnList = "ocorridoEm"),
        @Index(name = "idx_auditoria_recurso", columnList = "recurso,recursoId")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String acao;

    @Column(nullable = false, length = 100)
    private String recurso;

    private Long recursoId;

    @Column(nullable = false, length = 255)
    private String ator;

    @Column(nullable = false)
    private LocalDateTime ocorridoEm;

    @Column(length = 1000)
    private String motivo;

    public AuditoriaEvento(String acao, String recurso, Long recursoId, String ator, String motivo) {
        this.acao = acao;
        this.recurso = recurso;
        this.recursoId = recursoId;
        this.ator = ator;
        this.motivo = motivo;
        this.ocorridoEm = LocalDateTime.now();
    }
}
