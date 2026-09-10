package com.clickclak.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.TipoEvento;

public record MarcacionResponse(
    Long id,
    UUID uuidCliente,
    TipoEvento tipoEvento,
    Instant horaEvento,
    Instant horaSincronizacion,
    double latitud,
    double longitud,
    BigDecimal precisionMetros,
    BigDecimal distanciaMetros,
    EstadoValidacion estadoValidacion
) {
    public static MarcacionResponse desde(Marcacion marcacion) {
        return new MarcacionResponse(
            marcacion.getId(),
            marcacion.getUuidCliente(),
            marcacion.getTipoEvento(),
            marcacion.getHoraEvento(),
            marcacion.getHoraSincronizacion(),
            marcacion.getGeom().getY(),
            marcacion.getGeom().getX(),
            marcacion.getPrecisionMetros(),
            marcacion.getDistanciaMetros(),
            marcacion.getEstadoValidacion()
        );
    }
}
