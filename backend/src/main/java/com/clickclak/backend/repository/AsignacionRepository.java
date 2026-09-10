package com.clickclak.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clickclak.backend.model.Asignacion;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    @Query("""
        SELECT a FROM Asignacion a
        WHERE a.usuario.id = :usuarioId
          AND a.activo = true
          AND a.fechaInicio <= :fecha
          AND (a.fechaFin IS NULL OR a.fechaFin >= :fecha)
        """)
    Optional<Asignacion> buscarVigente(@Param("usuarioId") Long usuarioId, @Param("fecha") LocalDate fecha);

    /** Todas las asignaciones activas de un colaborador, usadas tanto para detectar
     * conflictos de fechas al crear una nueva como para armar su agenda. */
    List<Asignacion> findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(Long usuarioId);

    List<Asignacion> findAllByOrderByFechaInicioDesc();
}
