package org.example.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pacientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 200, message = "Nome deve ter no máximo 200 caracteres")
    private String nome;

    @Size(max = 14, message = "CPF inválido")
    private String cpf;

    @Size(max = 20, message = "Telefone muito longo")
    private String telefone;

    @Email(message = "E-mail inválido")
    @Size(max = 254, message = "E-mail muito longo")
    private String email;

    @Size(max = 500, message = "Endereço muito longo")
    private String endereco;

    @Size(max = 2000, message = "Observações muito longas")
    private String observacoes;

    @Min(value = 0, message = "Quantidade de kits não pode ser negativa")
    @Max(value = 9999, message = "Quantidade de kits excede o limite")
    private Integer quantidadeKits;

    @Size(max = 100)
    private String codigoRastreio;

    @Size(max = 100)
    private String codigoIdentificacao;

    // "sim", "nao", ""
    private String kitEntregueHoje;

    private String dataSaidaEstoque;
    private String dataEntrega;
    private String dataPrevisao;
    private Boolean atrasado;
    private String dataColetaProcesso;
    private String dataColetaRealizada;
    private String dataEmMaoBrasil;
    private String dataEnviadoEspanha;

    // "Pendente", "Em Andamento", "Concluído", "Rascunho"
    @Size(max = 100)
    private String statusResultado;

    @Size(max = 5000)
    private String resultado;

    // Campos legados (não usados ativamente)
    private String etapa;
    private String resultadoExame;

    // Anexos
    @Size(max = 255)
    private String anexoNome;

    @Size(max = 500)
    private String anexoCaminho;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
