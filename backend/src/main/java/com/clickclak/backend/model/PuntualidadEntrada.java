package com.clickclak.backend.model;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Clasificación de una entrada contra el turno de su asignación. {@code minutosRespectoAlInicio} es
 * negativo si llegó antes de la hora de inicio, 0 o positivo si llegó a la hora o después (los minutos
 * de retraso se cuentan desde el inicio, igual que en la incidencia de tardanza).
 *
 * <p>La hora se compara en Lima, que es donde trabaja el personal. Para turnos que cruzan la medianoche la
 * diferencia se toma por el camino más corto (una entrada a las 00:10 de un turno de 22:00 es tardía por 2 h
 * 10 min, no «temprana» por 21 h 50 min); el motor de validación contextual no hace ese ajuste, así que en
 * esos turnos la tardanza mostrada puede no tener incidencia automática.
 */
public record PuntualidadEntrada(EstadoPuntualidad estado, long minutosRespectoAlInicio) {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final long SEGUNDOS_POR_DIA = 24 * 3600L;

    public static PuntualidadEntrada clasificar(Instant horaEvento, Horario horario) {
        long segundosEvento = horaEvento.atZone(ZONA_LIMA).toLocalTime().toSecondOfDay();
        long diferencia = segundosEvento - horario.getHoraInicio().toSecondOfDay();
        if (diferencia > SEGUNDOS_POR_DIA / 2) {
            diferencia -= SEGUNDOS_POR_DIA;
        } else if (diferencia < -SEGUNDOS_POR_DIA / 2) {
            diferencia += SEGUNDOS_POR_DIA;
        }

        EstadoPuntualidad estado;
        if (diferencia < 0) {
            estado = EstadoPuntualidad.TEMPRANO;
        } else if (diferencia <= horario.getToleranciaMinutos() * 60L) {
            estado = EstadoPuntualidad.DENTRO_DE_TOLERANCIA;
        } else {
            estado = EstadoPuntualidad.TARDE;
        }
        return new PuntualidadEntrada(estado, diferencia / 60);
    }
}
