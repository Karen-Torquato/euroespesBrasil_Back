package org.example.repositories;

import org.example.models.UsuarioPermissao;
import org.example.models.UsuarioPermissaoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UsuarioPermissaoRepository extends JpaRepository<UsuarioPermissao, UsuarioPermissaoId> {
    @Query("select up from UsuarioPermissao up join fetch up.permissao where up.usuario.id = :usuarioId")
    List<UsuarioPermissao> findAllByUsuarioId(@Param("usuarioId") Long usuarioId);
}
