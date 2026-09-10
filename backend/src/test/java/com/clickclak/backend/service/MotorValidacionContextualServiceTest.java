package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.TipoEvento;

class MotorValidacionContextualServiceTest {

    private final MotorValidacionContextualService motor = new MotorValidacionContextualService();

    @Test
    void marcacionClaramenteDentroDeTolerancia_esValida() {
        var resultado = motor.validarUbicacion(50, BigDecimal.valueOf(10), 150);

        assertThat(resultado.estado()).isEqualTo(EstadoValidacion.VALIDO);
    }

    @Test
    void marcacionClaramenteFueraDeTolerancia_quedaFueraDeTolerancia() {
        var resultado = motor.validarUbicacion(500, BigDecimal.valueOf(10), 150);

        assertThat(resultado.estado()).isEqualTo(EstadoValidacion.FUERA_DE_TOLERANCIA);
    }

    @Test
    void marcacionEnZonaAmbiguaPorPrecisionDelGps_quedaObservada() {
        // distancia=160, precision=20, tolerancia=150: en el peor caso (180) no cumple,
        // pero en el mejor caso (140) sí — no se puede afirmar ni rechazar con certeza.
        var resultado = motor.validarUbicacion(160, BigDecimal.valueOf(20), 150);

        assertThat(resultado.estado()).isEqualTo(EstadoValidacion.OBSERVADO);
    }

    @Test
    void sinLecturaDePrecision_esSospechosa() {
        var resultado = motor.validarUbicacion(50, null, 150);

        assertThat(resultado.estado()).isEqualTo(EstadoValidacion.SOSPECHOSO);
    }

    @Test
    void precisionDemasiadoImprecisaParaConfiar_esSospechosa() {
        var resultado = motor.validarUbicacion(50, BigDecimal.valueOf(600), 150);

        assertThat(resultado.estado()).isEqualTo(EstadoValidacion.SOSPECHOSO);
    }

    @Test
    void entradaDentroDeTolerancia_noEsTardanza() {
        Horario horario = horarioDe(9, 0, 10);
        Instant horaEvento = instanteLima(9, 8);

        var resultado = motor.validarPuntualidad(TipoEvento.ENTRADA, horaEvento, horario, ZoneId.of("America/Lima"));

        assertThat(resultado.tarde()).isFalse();
    }

    @Test
    void entradaFueraDeTolerancia_esTardanzaConMinutosCorrectos() {
        Horario horario = horarioDe(9, 0, 10);
        Instant horaEvento = instanteLima(9, 25);

        var resultado = motor.validarPuntualidad(TipoEvento.ENTRADA, horaEvento, horario, ZoneId.of("America/Lima"));

        assertThat(resultado.tarde()).isTrue();
        assertThat(resultado.minutosTarde()).isEqualTo(25);
    }

    @Test
    void salidaNuncaGeneraTardanza_soloAplicaAEntrada() {
        Horario horario = horarioDe(9, 0, 10);
        Instant horaEvento = instanteLima(20, 0);

        var resultado = motor.validarPuntualidad(TipoEvento.SALIDA, horaEvento, horario, ZoneId.of("America/Lima"));

        assertThat(resultado.tarde()).isFalse();
    }

    private static Horario horarioDe(int horaInicioH, int horaInicioM, int toleranciaMinutos) {
        return Horario.builder()
                .horaInicio(LocalTime.of(horaInicioH, horaInicioM))
                .horaFin(LocalTime.of(18, 0))
                .toleranciaMinutos(toleranciaMinutos)
                .build();
    }

    private static Instant instanteLima(int hora, int minuto) {
        ZoneOffset offsetLima = ZoneOffset.ofHours(-5); // Perú: UTC-5 fijo, sin horario de verano
        return ZonedDateTime.of(2026, 9, 5, hora, minuto, 0, 0, offsetLima).toInstant();
    }
}
