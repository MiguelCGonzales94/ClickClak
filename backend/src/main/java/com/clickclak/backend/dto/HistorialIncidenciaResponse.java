package com.clickclak.backend.dto;

import java.time.Instant;

import com.clickclak.backend.model.EstadoIncidencia;
import com.clickclak.backend.model.IncidenciaHistorial;

public record HistorialIncidenciaResponse(
    EstadoIncidencia estadoAnterior,
    EstadoIncidencia estadoNuevo,
    Long usuarioId,
    String nombreUsuario,
    String comentario,
    Instant creadoEn
) {
    public static HistorialIncidenciaResponse desde(IncidenciaHistorial registro) {
        return new HistorialIncidenciaResponse(
            registro.getEstadoAnterior(), registro.getEstadoNuevo(),
            registro.getUsuario().getId(), IncidenciaResponse.nombreCompleto(registro.getUsuario()),
            registro.getComentario(), registro.getCreadoEn());
    }
}
