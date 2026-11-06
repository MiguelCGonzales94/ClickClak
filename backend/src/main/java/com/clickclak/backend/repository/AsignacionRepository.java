package com.clickclak.backend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clickclak.backend.model.Asignacion;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    /**
     * Todas las asignaciones en curso del colaborador en esa fecha. Pueden ser varias: un técnico puede
     * estar asignado a más de una sede a la vez. El orden (inicio y luego id) hace determinista el
     * desempate cuando dos sedes quedan a la misma distancia.
     */
    @Query("""
        SELECT a FROM Asignacion a
        WHERE a.usuario.id = :usuarioId
          AND a.activo = true
          AND a.fechaInicio <= :fecha
          AND (a.fechaFin IS NULL OR a.fechaFin >= :fecha)
        ORDER BY a.fechaInicio ASC, a.id ASC
        """)
    List<Asignacion> buscarVigentes(@Param("usuarioId") Long usuarioId, @Param("fecha") LocalDate fecha);

    /** Todas las asignaciones activas de un colaborador, usadas tanto para detectar
     * conflictos de fechas al crear una nueva como para armar su agenda. */
    List<Asignacion> findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(Long usuarioId);

    /** La vista de supervisión no muestra las asignaciones quitadas (baja lógica). */
    List<Asignacion> findByActivoTrueOrderByFechaInicioDesc();

    /** ¿Alguna asignación activa usa esta sede hoy o más adelante? Impide desactivarla. */
    @Query("""
        SELECT COUNT(a) > 0 FROM Asignacion a
        WHERE a.ubicacion.id = :ubicacionId
          AND a.activo = true
          AND (a.fechaFin IS NULL OR a.fechaFin >= :hoy)
        """)
    boolean existeAsignacionVigenteOProgramada(@Param("ubicacionId") Long ubicacionId, @Param("hoy") LocalDate hoy);

    /** Lo mismo para un servicio completo: ¿alguna asignación activa en curso o futura lo usa? */
    @Query("""
        SELECT COUNT(a) > 0 FROM Asignacion a
        WHERE a.proyecto.id = :proyectoId
          AND a.activo = true
          AND (a.fechaFin IS NULL OR a.fechaFin >= :hoy)
        """)
    boolean existeAsignacionVigenteOProgramadaEnProyecto(@Param("proyectoId") Long proyectoId, @Param("hoy") LocalDate hoy);

    /** Cualquier asignación (activa o quitada) cuenta como historial: las marcaciones cuelgan de ellas. */
    boolean existsByUbicacionId(Long ubicacionId);

    boolean existsByProyectoId(Long proyectoId);
}
