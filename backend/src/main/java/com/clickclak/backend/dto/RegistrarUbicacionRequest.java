package com.clickclak.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** HU06: sede o zona autorizada dentro de un proyecto, con su radio de tolerancia geográfica. */
public record RegistrarUbicacionRequest(
    @NotNull Long proyectoId,
    @NotBlank String nombre,
    String direccionReferencia,
    @DecimalMin("-90.0") @DecimalMax("90.0") double latitud,
    @DecimalMin("-180.0") @DecimalMax("180.0") double longitud,
    @Positive Integer radioToleranciaMetros
) {
}
