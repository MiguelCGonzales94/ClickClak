package com.clickclak.backend.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.TipoEvento;

/**
 * Una marcación tal como la ve quien supervisa: quién, cuándo, dónde y qué dictaminó el motor de
 * validación. {@code proyecto}, {@code ubicacion} y {@code radioToleranciaMetros} son nulos cuando la
 * marcación quedó {@code SIN_ASIGNACION}. {@code retrasoSincronizacionSegundos} es la diferencia entre
 * cuándo ocurrió el evento y cuándo llegó al servidor: un valor alto indica que se registró sin
 * conexión y se sincronizó después (OE4).
 */
public record AsistenciaResponse(
    Long id,
    Long usuarioId,
    String nombreUsuario,
    TipoEvento tipoEvento,
    Instant horaEvento,
    Instant horaSincronizacion,
    long retrasoSincronizacionSegundos,
    EstadoValidacion estadoValidacion,
    BigDecimal distanciaMetros,
    BigDecimal precisionMetros,
    Double latitud,
    Double longitud,
    String proyecto,
    String ubicacion,
    Integer radioToleranciaMetros,
    String dispositivo
) {
    /** Debe llamarse dentro de una transacción o con las asociaciones ya cargadas (ver {@code conDetalle}). */
    public static AsistenciaResponse desde(Marcacion marcacion) {
        var asignacion = marcacion.getAsignacion();
        var ubicacion = asignacion != null ? asignacion.getUbicacion() : null;
        var proyecto = asignacion != null ? asignacion.getProyecto() : null;
        var usuario = marcacion.getUsuario();
        var geom = marcacion.getGeom();
        long retraso = marcacion.getHoraEvento() != null && marcacion.getHoraSincronizacion() != null
                ? Math.max(0, Duration.between(marcacion.getHoraEvento(), marcacion.getHoraSincronizacion()).toSeconds())
                : 0;

        return new AsistenciaResponse(
            marcacion.getId(),
            usuario.getId(),
            (usuario.getNombres() + " " + usuario.getApellidos()).trim(),
            marcacion.getTipoEvento(),
            marcacion.getHoraEvento(),
            marcacion.getHoraSincronizacion(),
            retraso,
            marcacion.getEstadoValidacion(),
            marcacion.getDistanciaMetros(),
            marcacion.getPrecisionMetros(),
            geom != null ? geom.getY() : null,
            geom != null ? geom.getX() : null,
            proyecto != null ? proyecto.getNombre() : null,
            ubicacion != null ? ubicacion.getNombre() : null,
            ubicacion != null ? ubicacion.getRadioToleranciaMetros() : null,
            marcacion.getDispositivo() != null ? marcacion.getDispositivo().getNombreDispositivo() : null
        );
    }
}
