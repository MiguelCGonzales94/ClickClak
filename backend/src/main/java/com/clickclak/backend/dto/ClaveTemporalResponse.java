package com.clickclak.backend.dto;

/**
 * HU04: la clave temporal se devuelve una sola vez, al restablecer. Nunca se guarda en claro,
 * ni se escribe en logs o bitácora: si se pierde, hay que restablecer de nuevo.
 */
public record ClaveTemporalResponse(String claveTemporal) {
}
