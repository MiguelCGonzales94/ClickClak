package com.clickclak.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HU06: un proyecto representa el servicio/cliente al que se asigna personal de campo. */
public record RegistrarProyectoRequest(
    @NotBlank @Size(max = 150) String nombre,
    @NotBlank @Size(max = 150) String cliente,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaFin
) {
}
