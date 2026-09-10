package com.clickclak.backend.dto;

import java.time.LocalTime;

import com.clickclak.backend.model.Horario;

public record HorarioResponse(
    Long id,
    String nombre,
    LocalTime horaInicio,
    LocalTime horaFin,
    LocalTime horaInicioRefrigerio,
    LocalTime horaFinRefrigerio,
    int toleranciaMinutos,
    String diasSemana
) {
    public static HorarioResponse desde(Horario horario) {
        return new HorarioResponse(
            horario.getId(), horario.getNombre(), horario.getHoraInicio(), horario.getHoraFin(),
            horario.getHoraInicioRefrigerio(), horario.getHoraFinRefrigerio(),
            horario.getToleranciaMinutos(), horario.getDiasSemana());
    }
}
