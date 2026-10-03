package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.clickclak.backend.model.TipoEvento;

/**
 * Comando para registrar una marcación. {@code uuidCliente} lo genera el dispositivo en el
 * momento del evento (offline) y permite deduplicar reintentos de sincronización.
 */
public record RegistrarMarcacionRequest(
    @NotNull UUID uuidCliente,
    @NotNull Long usuarioId,
    @NotNull Long dispositivoId,
    @NotNull TipoEvento tipoEvento,
    @NotNull Instant horaEvento,
    @DecimalMin("-90.0") @DecimalMax("90.0") double latitud,
    @DecimalMin("-180.0") @DecimalMax("180.0") double longitud,
    @PositiveOrZero @DecimalMax("100000") BigDecimal precisionMetros
) {
}
