package com.clickclak.backend.dto;

public record LoginResponse(
    String token,
    long expiraEnMinutos,
    String rol,
    String nombres,
    String apellidos,
    boolean debeCambiarClave
) {
}
