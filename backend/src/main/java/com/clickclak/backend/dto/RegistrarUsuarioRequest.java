package com.clickclak.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * HU04: alta de usuario. {@code password} solo aplica a SUPERVISOR/RRHH_ADMIN (acceden por
 * contraseña); un COLABORADOR nunca debe traer password — su acceso es por WebAuthn (HU05).
 */
public record RegistrarUsuarioRequest(
    @NotBlank String nombres,
    @NotBlank String apellidos,
    @NotBlank String tipoDocumento,
    @NotBlank String numeroDocumento,
    @NotBlank @Email String correo,
    @NotBlank String rol,
    String password
) {
}
