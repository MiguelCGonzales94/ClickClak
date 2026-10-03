package com.clickclak.backend.dto;

import java.time.LocalDate;

import com.clickclak.backend.model.TipoIncidencia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro de una incidencia laboral. {@code usuarioId} solo lo indica el personal de revisión
 * cuando registra a nombre de un colaborador; si es un colaborador quien registra, siempre
 * se toma el usuario autenticado y este campo debe venir vacío o ser el suyo.
 */
public record RegistrarIncidenciaRequest(
    Long usuarioId,
    Long marcacionId,
    @NotNull TipoIncidencia tipo,
    @NotNull LocalDate fechaEvento,
    @NotBlank @Size(max = 2000) String descripcion
) {
}
