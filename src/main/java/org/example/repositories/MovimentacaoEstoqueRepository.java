package org.example.repositories;

import org.example.models.MovimentacaoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {
    List<MovimentacaoEstoque> findAllByOrderByDataDesc();

    @Query("""
            select m
            from MovimentacaoEstoque m
            left join fetch m.usuario
            where m.produto.id = :produtoId
            order by m.registradoEm desc, m.data desc
            """)
    List<MovimentacaoEstoque> findAuditoriaByProdutoId(@Param("produtoId") Long produtoId);
}
