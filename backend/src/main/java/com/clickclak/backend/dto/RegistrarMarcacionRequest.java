package com.clickclak.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.clickclak.backend.model.TipoEvento;

/**
 * Comando para registrar una marcación. {@code uuidCliente} lo genera el dispositivo en el
 * momento del evento (offline) y permite deduplicar reintentos de sincronización.
 */
public record RegistrarMarcacionRequest(
    UUID uuidCliente,
    Long usuarioId,
    Long dispositivoId,
    TipoEvento tipoEvento,
    Instant horaEvento,
    double latitud,
    double longitud,
    BigDecimal precisionMetros
) {
}
