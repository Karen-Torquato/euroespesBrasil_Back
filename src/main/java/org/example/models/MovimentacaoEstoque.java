package org.example.models;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import org.example.models.Usuario;
import org.example.models.Produto;

@Entity
@Table(name = "movimentacoes_estoque", indexes = {
    @Index(name = "idx_movimentacao_data", columnList = "data"),
    @Index(name = "idx_movimentacao_tipo", columnList = "tipo")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "ENTRADA" ou "SAIDA"
    private String tipo;

    private Integer quantidade;

    private String motivo;

    @Column(name = "saldo_anterior")
    private Integer saldoAnterior;

    @Column(name = "saldo_posterior")
    private Integer saldoPosterior;

    @Column(name = "registrado_em", updatable = false)
    private LocalDateTime registradoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonIgnore
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    @JsonIgnore
    private Produto produto;

    @Column(nullable = false, updatable = false)
    private LocalDateTime data;

    @PrePersist
    protected void onCreate() {
        this.registradoEm = this.registradoEm == null ? LocalDateTime.now() : this.registradoEm;
        this.data = LocalDateTime.now();
    }
}
