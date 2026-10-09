package com.clickclak.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * HU04: búsqueda paginada del panel. {@code patron} ya viene en minúsculas, con comodines y
     * escapado (nunca nulo: un parámetro de texto nulo dentro de LIKE no tiene tipo en Postgres).
     * {@code estado} sigue la precedencia de {@code EstadoCuenta}; BLOQUEADA no está en la base,
     * por eso {@code bloqueados} trae los correos con bloqueo vigente (nunca vacío, ver el servicio).
     */
    @Query("""
        SELECT u FROM Usuario u
        WHERE (:rol IS NULL OR u.rol.nombre = :rol)
          AND (lower(u.nombres) LIKE :patron ESCAPE '\\'
               OR lower(u.apellidos) LIKE :patron ESCAPE '\\'
               OR lower(u.correo) LIKE :patron ESCAPE '\\'
               OR u.numeroDocumento LIKE :patron ESCAPE '\\')
          AND (:estado IS NULL
               OR (:estado = 'INACTIVA' AND u.activo = false)
               OR (:estado = 'BLOQUEADA' AND u.activo = true AND u.correo IN :bloqueados)
               OR (:estado = 'CLAVE_PENDIENTE' AND u.activo = true AND u.debeCambiarClave = true
                   AND u.correo NOT IN :bloqueados)
               OR (:estado = 'ACTIVA' AND u.activo = true AND u.debeCambiarClave = false
                   AND u.correo NOT IN :bloqueados))
        ORDER BY u.nombres, u.apellidos, u.id
        """)
    Page<Usuario> buscarPagina(
            @Param("patron") String patron,
            @Param("rol") String rol,
            @Param("estado") String estado,
            @Param("bloqueados") Collection<String> bloqueados,
            Pageable paginacion);

    /**
     * Verdadero si alguna tabla con clave foránea hacia {@code usuario} lo referencia. Son las ocho
     * del esquema V1 (todas NO ACTION): si aparece una nueva, agregarla aquí; mientras tanto la
     * base la rechazaría de todos modos y el servicio lo traduce a 409.
     */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM asignacion WHERE usuario_id = :id)
            OR EXISTS (SELECT 1 FROM bitacora_auditoria WHERE usuario_id = :id)
            OR EXISTS (SELECT 1 FROM dispositivo WHERE usuario_id = :id)
            OR EXISTS (SELECT 1 FROM incidencia WHERE usuario_id = :id OR creado_por_id = :id OR revisado_por_id = :id)
            OR EXISTS (SELECT 1 FROM incidencia_historial WHERE usuario_id = :id)
            OR EXISTS (SELECT 1 FROM marcacion WHERE usuario_id = :id)
        """, nativeQuery = true)
    boolean tieneHistorial(@Param("id") Long id);
}
