package com.clickclak.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** HU08: cambio de servicio, sede, turno o fechas de una asignación existente. El técnico no se cambia: se crea otra. */
public record EditarAsignacionRequest(
    @NotNull Long proyectoId,
    @NotNull Long ubicacionId,
    @NotNull Long horarioId,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaFin
) {
}
