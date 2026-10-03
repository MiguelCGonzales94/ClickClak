package com.clickclak.backend.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

/** HU07: turno de trabajo — inicio, fin y tolerancia de puntualidad que usa el motor contextual. */
public record RegistrarHorarioRequest(
    @NotBlank @Size(max = 100) String nombre,
    @NotNull LocalTime horaInicio,
    @NotNull LocalTime horaFin,
    LocalTime horaInicioRefrigerio,
    LocalTime horaFinRefrigerio,
    @PositiveOrZero @Max(240) Integer toleranciaMinutos,
    @Size(max = 20) @Pattern(regexp = "^[LMXJVSD](,[LMXJVSD])*$", message = "debe ser una lista de días separada por comas (L,M,X,J,V,S,D)") String diasSemana
) {
}
