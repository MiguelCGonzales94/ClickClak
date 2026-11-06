package com.clickclak.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corrige un servicio (proyecto/cliente) ya registrado. Activarlo o desactivarlo tiene sus propios endpoints. */
public record EditarProyectoRequest(
    @NotBlank @Size(max = 150) String nombre,
    @NotBlank @Size(max = 150) String cliente,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaFin
) {
}
