package com.clickclak.backend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clickclak.backend.model.EstadoIncidencia;
import com.clickclak.backend.model.Incidencia;
import com.clickclak.backend.model.TipoIncidencia;

import jakarta.persistence.LockModeType;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {
    List<Incidencia> findByUsuarioId(Long usuarioId);

    List<Incidencia> findByEstado(EstadoIncidencia estado);

    /**
     * Bandeja filtrable por colaborador y/o estado, de la más reciente a la más antigua. Trae
     * de una vez los usuarios que el listado muestra para no disparar una consulta por fila.
     */
    @Query("""
        SELECT i FROM Incidencia i
        JOIN FETCH i.usuario
        JOIN FETCH i.creadoPor
        LEFT JOIN FETCH i.revisadoPor
        WHERE (:usuarioId IS NULL OR i.usuario.id = :usuarioId)
          AND (:estado IS NULL OR i.estado = :estado)
        ORDER BY i.fechaEvento DESC, i.id DESC
        """)
    List<Incidencia> buscar(@Param("usuarioId") Long usuarioId, @Param("estado") EstadoIncidencia estado);

    /**
     * Bloquea la fila hasta el fin de la transacción: dos revisores que actúan a la vez sobre
     * la misma incidencia se serializan y el segundo ve el estado ya cambiado, en vez de
     * aprobar y rechazar simultáneamente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Incidencia i WHERE i.id = :id")
    Optional<Incidencia> buscarParaActualizar(@Param("id") Long id);

    /** Evita registrar dos veces lo mismo mientras la primera incidencia siga abierta o aprobada. */
    boolean existsByUsuarioIdAndTipoAndFechaEventoAndEstadoIn(
            Long usuarioId, TipoIncidencia tipo, LocalDate fechaEvento, Collection<EstadoIncidencia> estados);
}
