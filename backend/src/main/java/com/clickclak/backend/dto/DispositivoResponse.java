package com.clickclak.backend.dto;

import java.time.Instant;

import com.clickclak.backend.model.Dispositivo;

/** Resumen seguro del dispositivo registrado para uso del cliente de campo. */
public record DispositivoResponse(
        Long id,
        String nombreDispositivo,
        boolean activo,
        Instant registradoEn
) {
    public static DispositivoResponse desde(Dispositivo dispositivo) {
        return new DispositivoResponse(
                dispositivo.getId(),
                dispositivo.getNombreDispositivo(),
                dispositivo.isActivo(),
                dispositivo.getRegistradoEn());
    }
}
