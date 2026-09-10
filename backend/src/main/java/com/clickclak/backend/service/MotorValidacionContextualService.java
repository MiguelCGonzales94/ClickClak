package com.clickclak.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.TipoEvento;

/**
 * Motor de validación contextual (OE3): contrasta cada marcación contra la ubicación y el
 * horario de la asignación vigente, produciendo un estado graduado — nunca un juicio binario,
 * porque la precisión de la geolocalización es variable y se degrada en interiores.
 *
 * <p>Los umbrales son parámetros de negocio ajustables, no reglas fijas del dominio; sus
 * valores actuales son una recomendación inicial razonable, pendiente de calibrar con datos
 * reales de uso.
 */
@Service
public class MotorValidacionContextualService {

    /** Por encima de esta precisión reportada por el GPS, la lectura no es confiable. */
    private static final double PRECISION_MAXIMA_CONFIABLE_METROS = 500.0;

    public record ResultadoValidacionEspacial(EstadoValidacion estado) {
    }

    public record ResultadoPuntualidad(boolean tarde, long minutosTarde) {
    }

    /**
     * @param distanciaMetros        distancia real, ya calculada contra la ubicación de la asignación
     * @param precisionMetros        precisión reportada por la API de geolocalización del navegador (accuracy)
     * @param radioToleranciaMetros  margen configurado en la ubicación
     */
    public ResultadoValidacionEspacial validarUbicacion(
            double distanciaMetros, BigDecimal precisionMetros, int radioToleranciaMetros) {

        if (precisionMetros == null || precisionMetros.doubleValue() > PRECISION_MAXIMA_CONFIABLE_METROS) {
            return new ResultadoValidacionEspacial(EstadoValidacion.SOSPECHOSO);
        }

        double precision = precisionMetros.doubleValue();
        // Caso pesimista: aun si el GPS se equivocó en contra del colaborador, ¿sigue dentro?
        double distanciaPesimista = distanciaMetros + precision;
        // Caso optimista: si el GPS se equivocó a su favor, ¿podría estar dentro?
        double distanciaOptimista = Math.max(0, distanciaMetros - precision);

        if (distanciaPesimista <= radioToleranciaMetros) {
            return new ResultadoValidacionEspacial(EstadoValidacion.VALIDO);
        }
        if (distanciaOptimista <= radioToleranciaMetros) {
            // Ambiguo dado el margen de error del GPS: se acepta y se marca para revisión,
            // nunca se rechaza solo por esto.
            return new ResultadoValidacionEspacial(EstadoValidacion.OBSERVADO);
        }
        return new ResultadoValidacionEspacial(EstadoValidacion.FUERA_DE_TOLERANCIA);
    }

    /**
     * Solo aplica a eventos de tipo {@link TipoEvento#ENTRADA}: compara la hora de ingreso
     * contra el inicio de horario más su tolerancia.
     */
    public ResultadoPuntualidad validarPuntualidad(
            TipoEvento tipoEvento, Instant horaEvento, Horario horario, ZoneId zonaHoraria) {

        if (tipoEvento != TipoEvento.ENTRADA) {
            return new ResultadoPuntualidad(false, 0);
        }

        ZonedDateTime eventoLocal = horaEvento.atZone(zonaHoraria);
        int segundosEvento = eventoLocal.toLocalTime().toSecondOfDay();
        int segundosInicio = horario.getHoraInicio().toSecondOfDay();
        int segundosLimite = segundosInicio + (horario.getToleranciaMinutos() * 60);

        if (segundosEvento <= segundosLimite) {
            return new ResultadoPuntualidad(false, 0);
        }

        long minutosTarde = (segundosEvento - segundosInicio) / 60;
        return new ResultadoPuntualidad(true, minutosTarde);
    }
}
