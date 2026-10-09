package com.clickclak.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * HU08: pasar a un técnico de su sede actual a otra a partir de {@code fechaCambio}. La asignación
 * actual termina el día anterior y nace otra en la sede nueva, en una sola operación. {@code proyectoId}
 * y {@code horarioId} son opcionales: si faltan se conservan los de la asignación que se deja.
 */
public record MoverAsignacionRequest(
    @NotNull Long ubicacionId,
    Long proyectoId,
    Long horarioId,
    @NotNull LocalDate fechaCambio
) {
}
