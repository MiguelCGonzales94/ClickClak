package com.clickclak.backend.dto;

import java.time.Instant;

import com.clickclak.backend.model.AccionAuditoria;
import com.fasterxml.jackson.databind.JsonNode;

/** HU04: una entrada de la línea de tiempo de un usuario, tomada de la bitácora de auditoría. */
public record HistorialUsuarioResponse(
    Long id,
    AccionAuditoria accion,
    Long actorId,
    String actorNombre,
    JsonNode valoresAnteriores,
    JsonNode valoresNuevos,
    Instant creadoEn
) {
}
