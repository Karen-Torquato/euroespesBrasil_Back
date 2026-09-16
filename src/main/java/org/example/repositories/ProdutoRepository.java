package org.example.repositories;

import jakarta.persistence.LockModeType;
import org.example.models.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Optional<Produto> findByCodigoProdutoAndCodigoSerie(String codigoProduto, String codigoSerie);

    // Lock pessimista: garante que dois pedidos simultâneos não gerem a mesma sequência
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id")
    Optional<Produto> findByIdForUpdate(@Param("id") Long id);
}
