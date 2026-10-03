package com.clickclak.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU04: alta de usuario. {@code password} solo aplica a SUPERVISOR/RRHH_ADMIN (acceden por
 * contraseña); un COLABORADOR nunca debe traer password — su acceso es por WebAuthn (HU05).
 */
public record RegistrarUsuarioRequest(
    @NotBlank @Size(max = 100) String nombres,
    @NotBlank @Size(max = 100) String apellidos,
    @NotBlank @Size(max = 20) String tipoDocumento,
    @NotBlank @Size(max = 20) String numeroDocumento,
    @NotBlank @Email @Size(max = 150) String correo,
    @NotBlank @Size(max = 30) String rol,
    @Size(max = 72) String password
) {
}
