package org.example.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "Nome do médico é obrigatório")
    @Size(max = 200, message = "Nome do médico deve ter no máximo 200 caracteres")
    private String nome;

    @NotBlank(message = "CPF do médico é obrigatório")
    @Size(max = 14, message = "CPF inválido")
    private String cpf;

    @NotBlank(message = "CRM do médico é obrigatório")
    @Size(max = 30, message = "CRM inválido")
    private String crm;

    private String email;
    private String telefone;

    @NotBlank(message = "Especialidade do médico é obrigatória")
    @Size(max = 120, message = "Especialidade inválida")
    private String especialidade;

    @Column(name = "estoque_privado", nullable = false)
    private boolean estoquePrivado = false;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        this.criadoEm = this.criadoEm == null ? LocalDateTime.now() : this.criadoEm;
        this.atualizadoEm = this.atualizadoEm == null ? this.criadoEm : this.atualizadoEm;
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
