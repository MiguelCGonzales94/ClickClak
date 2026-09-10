package com.clickclak.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clickclak.backend.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);

    /** HU04: listado filtrable por rol y/o estado, usado también para elegir técnicos (HU05/HU08). */
    @Query("""
        SELECT u FROM Usuario u
        WHERE (:rol IS NULL OR u.rol.nombre = :rol)
          AND (:activo IS NULL OR u.activo = :activo)
        ORDER BY u.nombres, u.apellidos
        """)
    List<Usuario> buscar(@Param("rol") String rol, @Param("activo") Boolean activo);
}
