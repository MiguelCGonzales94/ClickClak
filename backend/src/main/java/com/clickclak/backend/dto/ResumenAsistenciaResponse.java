package com.clickclak.backend.dto;

import java.util.Map;

import com.clickclak.backend.model.EstadoValidacion;

/** Conteo de marcaciones por estado de validación para los mismos filtros de la lista (menos el estado). */
public record ResumenAsistenciaResponse(long total, Map<EstadoValidacion, Long> porEstado) {
}
