package com.clickclak.backend.dto;

/**
 * {@code opcionesJson} es el objeto listo para pasarle a {@code navigator.credentials.create()}
 * o {@code .get()} en el navegador (ya viene envuelto como {@code {"publicKey": {...}}}).
 * {@code idSolicitud} se debe reenviar tal cual al llamar a "finalizar".
 */
public record OpcionesWebAuthnResponse(
    String idSolicitud,
    String opcionesJson
) {
}
