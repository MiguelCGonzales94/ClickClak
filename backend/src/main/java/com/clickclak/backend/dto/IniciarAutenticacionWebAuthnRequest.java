package com.clickclak.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IniciarAutenticacionWebAuthnRequest(
    @NotBlank @Email @Size(max = 150) String correo
) {
}
