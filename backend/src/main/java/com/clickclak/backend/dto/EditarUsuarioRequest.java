package com.clickclak.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU04: edición de datos y rol. {@code password} solo se acepta cuando el cambio de rol lo exige
 * (de COLABORADOR a SUPERVISOR/RRHH_ADMIN, que antes no tenía contraseña); el resto de cambios de
 * clave van por {@code /api/usuarios/{id}/restablecer-clave} o {@code /api/auth/cambiar-clave}.
 */
public record EditarUsuarioRequest(
    @NotBlank @Size(max = 100) String nombres,
    @NotBlank @Size(max = 100) String apellidos,
    @NotBlank @Size(max = 20) String tipoDocumento,
    @NotBlank @Size(max = 20) String numeroDocumento,
    @NotBlank @Email @Size(max = 150) String correo,
    @NotBlank @Size(max = 30) String rol,
    @Size(max = 72) String password
) {
}
