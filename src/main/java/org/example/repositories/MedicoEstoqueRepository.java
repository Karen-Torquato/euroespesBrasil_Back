package org.example.repositories;

import org.example.models.MedicoEstoque;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicoEstoqueRepository extends JpaRepository<MedicoEstoque, Long> {
    List<MedicoEstoque> findAllByMedico_Id(Long medicoId);

    @Query("""
            select me.id as id,
                   p.id as produtoId,
                   p.nome as produtoNome,
                   me.quantidadeAtual as quantidadeAtual,
                   me.estoqueMinimo as estoqueMinimo
            from MedicoEstoque me
            join me.produto p
            where me.medico.id = :medicoId
            order by me.id asc
            """)
    List<MedicoEstoqueResumoProjection> findResumoByMedicoId(@Param("medicoId") Long medicoId);

    interface MedicoEstoqueResumoProjection {
        Long getId();
        Long getProdutoId();
        String getProdutoNome();
        Integer getQuantidadeAtual();
        Integer getEstoqueMinimo();
    }
}
