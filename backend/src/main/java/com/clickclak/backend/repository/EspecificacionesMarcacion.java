package com.clickclak.backend.repository;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.TipoEvento;

import jakarta.persistence.criteria.JoinType;

/**
 * Filtros combinables de la consulta de asistencia (HU de supervisión). Cada filtro se agrega solo
 * si viene informado: un parámetro nulo dentro de una consulta JPQL fija no tiene tipo en Postgres,
 * y con especificaciones nunca llega a la base.
 */
public final class EspecificacionesMarcacion {

    private EspecificacionesMarcacion() {
    }

    /**
     * Trae en la misma consulta todo lo que muestra la pantalla (persona, asignación, sede,
     * proyecto y dispositivo): son asociaciones perezosas y sin esto cada fila costaría varias
     * consultas. La consulta de conteo de la paginación no puede llevar fetch, por eso se omite.
     */
    public static Specification<Marcacion> conDetalle() {
        return (raiz, consulta, constructor) -> {
            if (consulta.getResultType() != Long.class && consulta.getResultType() != long.class) {
                raiz.fetch("usuario", JoinType.INNER);
                var asignacion = raiz.fetch("asignacion", JoinType.LEFT);
                asignacion.fetch("ubicacion", JoinType.LEFT);
                asignacion.fetch("proyecto", JoinType.LEFT);
                raiz.fetch("dispositivo", JoinType.INNER);
            }
            return constructor.conjunction();
        };
    }

    public static Specification<Marcacion> deUsuario(Long usuarioId) {
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("usuario").get("id"), usuarioId);
    }

    public static Specification<Marcacion> deProyecto(Long proyectoId) {
        return (raiz, consulta, constructor) ->
                constructor.equal(raiz.get("asignacion").get("proyecto").get("id"), proyectoId);
    }

    public static Specification<Marcacion> deUbicacion(Long ubicacionId) {
        return (raiz, consulta, constructor) ->
                constructor.equal(raiz.get("asignacion").get("ubicacion").get("id"), ubicacionId);
    }

    /** {@code desde} inclusivo, {@code hasta} exclusivo: así un día completo es [00:00, 00:00 del siguiente). */
    public static Specification<Marcacion> desde(Instant desde) {
        return (raiz, consulta, constructor) -> constructor.greaterThanOrEqualTo(raiz.get("horaEvento"), desde);
    }

    public static Specification<Marcacion> hasta(Instant hastaExclusivo) {
        return (raiz, consulta, constructor) -> constructor.lessThan(raiz.get("horaEvento"), hastaExclusivo);
    }

    public static Specification<Marcacion> conEstado(EstadoValidacion estado) {
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("estadoValidacion"), estado);
    }

    public static Specification<Marcacion> deTipo(TipoEvento tipo) {
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("tipoEvento"), tipo);
    }
}
