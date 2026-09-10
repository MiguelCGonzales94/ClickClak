package com.clickclak.backend.dto;

import java.time.LocalDate;

import com.clickclak.backend.model.Proyecto;

public record ProyectoResponse(
    Long id,
    String nombre,
    String cliente,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    boolean activo
) {
    public static ProyectoResponse desde(Proyecto proyecto) {
        return new ProyectoResponse(
            proyecto.getId(), proyecto.getNombre(), proyecto.getCliente(),
            proyecto.getFechaInicio(), proyecto.getFechaFin(), proyecto.isActivo());
    }
}
