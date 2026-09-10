package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record FinalizarAutenticacionWebAuthnRequest(
    @NotBlank String idSolicitud,
    /** JSON producido por {@code credential.toJSON()} en el navegador. */
    @NotBlank String credencialJson
) {
}
