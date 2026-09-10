package com.clickclak.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** HU08: vínculo colaborador-proyecto-ubicación-horario vigente en un rango de fechas. */
public record RegistrarAsignacionRequest(
    @NotNull Long usuarioId,
    @NotNull Long proyectoId,
    @NotNull Long ubicacionId,
    @NotNull Long horarioId,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaFin
) {
}
