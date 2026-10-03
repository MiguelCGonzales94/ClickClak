package com.clickclak.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.clickclak.backend.model.EstadoIncidencia;
import com.clickclak.backend.model.Incidencia;
import com.clickclak.backend.model.TipoIncidencia;
import com.clickclak.backend.model.Usuario;

public record IncidenciaResponse(
    Long id,
    Long usuarioId,
    String nombreUsuario,
    Long marcacionId,
    TipoIncidencia tipo,
    EstadoIncidencia estado,
    LocalDate fechaEvento,
    String descripcion,
    Long creadoPorId,
    String nombreCreadoPor,
    Long revisadoPorId,
    String nombreRevisadoPor,
    String comentarioRevision,
    Instant revisadoEn,
    Instant creadoEn,
    Instant actualizadoEn
) {
    /** Recorre asociaciones perezosas: debe invocarse dentro de la transacción del servicio. */
    public static IncidenciaResponse desde(Incidencia incidencia) {
        Usuario revisor = incidencia.getRevisadoPor();
        return new IncidenciaResponse(
            incidencia.getId(),
            incidencia.getUsuario().getId(), nombreCompleto(incidencia.getUsuario()),
            incidencia.getMarcacion() == null ? null : incidencia.getMarcacion().getId(),
            incidencia.getTipo(), incidencia.getEstado(), incidencia.getFechaEvento(), incidencia.getDescripcion(),
            incidencia.getCreadoPor().getId(), nombreCompleto(incidencia.getCreadoPor()),
            revisor == null ? null : revisor.getId(), revisor == null ? null : nombreCompleto(revisor),
            incidencia.getComentarioRevision(), incidencia.getRevisadoEn(),
            incidencia.getCreadoEn(), incidencia.getActualizadoEn());
    }

    static String nombreCompleto(Usuario usuario) {
        return usuario.getNombres() + " " + usuario.getApellidos();
    }
}
