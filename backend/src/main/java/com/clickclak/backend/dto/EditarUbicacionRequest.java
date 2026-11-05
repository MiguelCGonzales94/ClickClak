package com.clickclak.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corrige una sede ya registrada: su nombre, referencia y la zona (centro y radio). El servicio al
 * que pertenece no cambia; para eso se registra otra sede. El radio es obligatorio al editar
 * porque la pantalla siempre lo envía y así no se pierde por un campo omitido.
 */
public record EditarUbicacionRequest(
    @NotBlank @Size(max = 150) String nombre,
    @Size(max = 255) String direccionReferencia,
    @DecimalMin("-90.0") @DecimalMax("90.0") double latitud,
    @DecimalMin("-180.0") @DecimalMax("180.0") double longitud,
    @NotNull @Positive @Max(5000) Integer radioToleranciaMetros
) {
}
