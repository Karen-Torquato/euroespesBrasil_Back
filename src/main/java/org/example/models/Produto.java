package org.example.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "produtos", uniqueConstraints = @UniqueConstraint(columnNames = {"codigoProduto", "codigoSerie"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome do produto é obrigatório")
    @Size(max = 150)
    private String nome;

    @NotBlank(message = "Código do produto é obrigatório")
    @Size(max = 20)
    private String codigoProduto;

    @NotBlank(message = "Código de série é obrigatório")
    @Size(max = 20)
    private String codigoSerie;

    @Min(value = 0, message = "Estoque não pode ser negativo")
    private Integer estoqueAtual = 0;

    @Min(value = 0, message = "Estoque mínimo não pode ser negativo")
    private Integer estoqueMinimo = 5;

    // Contador global de unidades já vendidas deste produto - usado para gerar o código sequencial
    private Integer ultimaSequencia = 0;
}
