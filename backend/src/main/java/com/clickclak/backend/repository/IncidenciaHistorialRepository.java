package com.clickclak.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clickclak.backend.model.IncidenciaHistorial;

public interface IncidenciaHistorialRepository extends JpaRepository<IncidenciaHistorial, Long> {
    List<IncidenciaHistorial> findByIncidenciaIdOrderByCreadoEnAsc(Long incidenciaId);

    /** Igual que la anterior pero con el usuario ya cargado, para armar la traza en una sola consulta. */
    @Query("""
        SELECT h FROM IncidenciaHistorial h
        JOIN FETCH h.usuario
        WHERE h.incidencia.id = :incidenciaId
        ORDER BY h.creadoEn ASC, h.id ASC
        """)
    List<IncidenciaHistorial> trazaDe(@Param("incidenciaId") Long incidenciaId);
}
