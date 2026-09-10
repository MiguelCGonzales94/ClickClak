package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotNull;

/** Lo dispara un Supervisor/RRHH con el técnico presente — ver WebAuthnController. */
public record IniciarRegistroWebAuthnRequest(
    @NotNull Long usuarioId
) {
}
