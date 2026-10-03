package com.clickclak.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU04: edición de datos y rol. El cambio de contraseña queda fuera (ver HU03, recuperación de cuenta). */
public record EditarUsuarioRequest(
    @NotBlank @Size(max = 100) String nombres,
    @NotBlank @Size(max = 100) String apellidos,
    @NotBlank @Size(max = 20) String tipoDocumento,
    @NotBlank @Size(max = 20) String numeroDocumento,
    @NotBlank @Email @Size(max = 150) String correo,
    @NotBlank @Size(max = 30) String rol
) {
}
