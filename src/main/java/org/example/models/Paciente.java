package org.example.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
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
    private String nome;

    private String cpf;

    private String telefone;

    private String email;

    private String endereco;

    private String observacoes;

    private Integer quantidadeKits;

    private String codigoRastreio;

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
    private String statusResultado;

    private String resultado;

    // Campos legados (não usados ativamente)
    private String etapa;
    private String resultadoExame;

    // Anexos
    private String anexoNome;
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

    // Getters e setters com @JsonIgnore para timestamps
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

