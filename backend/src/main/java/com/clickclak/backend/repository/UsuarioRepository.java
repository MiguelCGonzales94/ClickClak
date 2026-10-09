package com.clickclak.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clickclak.backend.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    /** Sin distinguir mayúsculas, igual que el índice único {@code uk_usuario_correo_minusculas} (V3). */
    @Query("SELECT u FROM Usuario u WHERE lower(u.correo) = lower(:correo)")
    Optional<Usuario> findByCorreo(@Param("correo") String correo);

    /** Para el filtro JWT: trae el rol en la misma consulta porque la asociación es perezosa. */
    @Query("SELECT u FROM Usuario u JOIN FETCH u.rol WHERE u.id = :id")
    Optional<Usuario> findConRolById(@Param("id") Long id);

    long countByRolNombreAndActivoTrue(String nombreRol);

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
