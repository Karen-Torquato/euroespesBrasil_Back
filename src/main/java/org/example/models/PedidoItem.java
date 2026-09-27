package org.example.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "itens_pedido")
@Data
@NoArgsConstructor
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "paciente_id", nullable = false)
    @JsonIgnore
    private Paciente paciente;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    @JsonIgnore
    private Produto produto;

    @Column(nullable = false)
    private String codigoGerado;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        this.criadoEm = LocalDateTime.now();
    }

    @Transient
    public Long getProdutoId() {
        return produto != null ? produto.getId() : null;
    }

    @Transient
    public String getProdutoNome() {
        return produto != null ? produto.getNome() : null;
    }

    @Transient
    public String getCodigoProduto() {
        return produto != null ? produto.getCodigoProduto() : null;
    }
}
