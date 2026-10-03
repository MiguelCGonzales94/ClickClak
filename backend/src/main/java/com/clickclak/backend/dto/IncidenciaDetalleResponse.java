package com.clickclak.backend.dto;

import java.util.List;

/** Incidencia con su traza completa de cambios de estado, de la más antigua a la más reciente. */
public record IncidenciaDetalleResponse(
    IncidenciaResponse incidencia,
    List<HistorialIncidenciaResponse> historial
) {
}
