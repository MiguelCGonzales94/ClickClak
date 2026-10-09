package com.clickclak.backend.dto;

import java.time.LocalDate;

import com.clickclak.backend.model.Asignacion;

public record AsignacionResponse(
    Long id,
    Long usuarioId,
    String nombreUsuario,
    Long proyectoId,
    String nombreProyecto,
    Long ubicacionId,
    String nombreUbicacion,
    Long horarioId,
    String nombreHorario,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    String estado
) {
    /**
     * {@code estado} se calcula contra {@code hoy}: no es una columna persistida (HU09). PROGRAMADA aún no
     * empezó, VIGENTE está en curso y FINALIZADA ya terminó (antes una asignación terminada seguía diciendo VIGENTE).
     */
    public static AsignacionResponse desde(Asignacion asignacion, LocalDate hoy) {
        String estado;
        if (asignacion.getFechaInicio().isAfter(hoy)) {
            estado = "PROGRAMADA";
        } else if (asignacion.getFechaFin() != null && asignacion.getFechaFin().isBefore(hoy)) {
            estado = "FINALIZADA";
        } else {
            estado = "VIGENTE";
        }
        return new AsignacionResponse(
            asignacion.getId(),
            asignacion.getUsuario().getId(), asignacion.getUsuario().getNombres() + " " + asignacion.getUsuario().getApellidos(),
            asignacion.getProyecto().getId(), asignacion.getProyecto().getNombre(),
            asignacion.getUbicacion().getId(), asignacion.getUbicacion().getNombre(),
            asignacion.getHorario().getId(), asignacion.getHorario().getNombre(),
            asignacion.getFechaInicio(), asignacion.getFechaFin(), estado);
    }
}
