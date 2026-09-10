package com.clickclak.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** HU04: edición de datos y rol. El cambio de contraseña queda fuera (ver HU03, recuperación de cuenta). */
public record EditarUsuarioRequest(
    @NotBlank String nombres,
    @NotBlank String apellidos,
    @NotBlank String tipoDocumento,
    @NotBlank String numeroDocumento,
    @NotBlank @Email String correo,
    @NotBlank String rol
) {
}
