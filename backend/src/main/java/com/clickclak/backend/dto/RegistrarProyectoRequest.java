package com.clickclak.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** HU06: un proyecto representa el servicio/cliente al que se asigna personal de campo. */
public record RegistrarProyectoRequest(
    @NotBlank String nombre,
    @NotBlank String cliente,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaFin
) {
}
