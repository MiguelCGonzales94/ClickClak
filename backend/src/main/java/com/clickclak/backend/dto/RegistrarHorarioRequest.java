package com.clickclak.backend.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** HU07: turno de trabajo — inicio, fin y tolerancia de puntualidad que usa el motor contextual. */
public record RegistrarHorarioRequest(
    @NotBlank String nombre,
    @NotNull LocalTime horaInicio,
    @NotNull LocalTime horaFin,
    LocalTime horaInicioRefrigerio,
    LocalTime horaFinRefrigerio,
    @PositiveOrZero Integer toleranciaMinutos,
    String diasSemana
) {
}
