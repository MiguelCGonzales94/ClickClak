package com.clickclak.backend.dto;

public record PerfilResponse(
    Long id,
    String nombres,
    String apellidos,
    String correo,
    String rol
) {
}
