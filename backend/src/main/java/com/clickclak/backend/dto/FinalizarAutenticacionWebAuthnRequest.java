package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinalizarAutenticacionWebAuthnRequest(
    @NotBlank @Size(max = 100) String idSolicitud,
    /** JSON producido por {@code credential.toJSON()} en el navegador. */
    @NotBlank @Size(max = 20000) String credencialJson
) {
}
