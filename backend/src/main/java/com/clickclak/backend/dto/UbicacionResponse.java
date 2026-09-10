package com.clickclak.backend.dto;

import com.clickclak.backend.model.Ubicacion;

public record UbicacionResponse(
    Long id,
    Long proyectoId,
    String nombre,
    String direccionReferencia,
    double latitud,
    double longitud,
    int radioToleranciaMetros,
    boolean activo
) {
    public static UbicacionResponse desde(Ubicacion ubicacion) {
        return new UbicacionResponse(
            ubicacion.getId(), ubicacion.getProyecto().getId(), ubicacion.getNombre(),
            ubicacion.getDireccionReferencia(), ubicacion.getGeom().getY(), ubicacion.getGeom().getX(),
            ubicacion.getRadioToleranciaMetros(), ubicacion.isActivo());
    }
}
