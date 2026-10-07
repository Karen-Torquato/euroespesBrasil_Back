package org.example.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioPermissaoId implements Serializable {

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "permissao_id")
    private Long permissaoId;
}
