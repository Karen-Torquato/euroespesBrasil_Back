package org.example.repositories;

import org.example.models.LoteProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoteProdutoRepository extends JpaRepository<LoteProduto, Long> {
    List<LoteProduto> findAllByProduto_IdOrderByDataEntradaDesc(Long produtoId);
}
