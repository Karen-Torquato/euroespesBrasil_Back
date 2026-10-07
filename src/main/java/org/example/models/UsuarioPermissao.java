package org.example.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_permissoes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioPermissao {

    @EmbeddedId
    private UsuarioPermissaoId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("usuarioId")
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("permissaoId")
    @JoinColumn(name = "permissao_id", nullable = false)
    private Permissao permissao;

    @Column(name = "concedido_em", nullable = false, updatable = false)
    private LocalDateTime concedidoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concedido_por_usuario_id")
    private Usuario concedidoPor;

    @PrePersist
    void prePersist() {
        this.concedidoEm = this.concedidoEm == null ? LocalDateTime.now() : this.concedidoEm;
    }
}
