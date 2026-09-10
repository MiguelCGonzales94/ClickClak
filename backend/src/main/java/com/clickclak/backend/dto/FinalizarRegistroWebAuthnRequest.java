package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record FinalizarRegistroWebAuthnRequest(
    @NotBlank String idSolicitud,
    /** JSON producido por {@code credential.toJSON()} en el navegador. */
    @NotBlank String credencialJson,
    String nombreDispositivo
) {
}
