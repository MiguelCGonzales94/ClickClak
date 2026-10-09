package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU04: cambio de la propia contraseña. Exige la actual aunque haya sesión abierta. */
public record CambiarClaveRequest(
    @NotBlank @Size(max = 72) String claveActual,
    @NotBlank @Size(max = 72) String claveNueva
) {
}
