package com.clickclak.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** Turno de 08:00 con 10 minutos de tolerancia; Lima es UTC-5, así que las 08:00 son las 13:00Z. */
class PuntualidadEntradaTest {

    private static final Horario TURNO = Horario.builder()
            .nombre("Mañana").horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(17, 0)).toleranciaMinutos(10).build();

    private static PuntualidadEntrada a(String horaUtc) {
        return PuntualidadEntrada.clasificar(Instant.parse("2026-11-05T" + horaUtc + "Z"), TURNO);
    }

    @Test
    void antesDelInicioEsTempranoConMinutosNegativos() {
        var r = a("12:45:00");
        assertThat(r.estado()).isEqualTo(EstadoPuntualidad.TEMPRANO);
        assertThat(r.minutosRespectoAlInicio()).isEqualTo(-15);
    }

    @Test
    void aLaHoraYHastaElFinDeLaToleranciaEsDentroDeTolerancia() {
        assertThat(a("13:00:00").estado()).isEqualTo(EstadoPuntualidad.DENTRO_DE_TOLERANCIA);
        assertThat(a("13:10:00").estado()).isEqualTo(EstadoPuntualidad.DENTRO_DE_TOLERANCIA);
        assertThat(a("13:10:00").minutosRespectoAlInicio()).isEqualTo(10);
    }

    @Test
    void unSegundoDespuesDeLaToleranciaYaEsTarde() {
        assertThat(a("13:10:01").estado()).isEqualTo(EstadoPuntualidad.TARDE);
        assertThat(a("13:11:00").minutosRespectoAlInicio()).isEqualTo(11);
    }

    @Test
    void unSegundoAntesDelInicioEsTemprano() {
        assertThat(a("12:59:59").estado()).isEqualTo(EstadoPuntualidad.TEMPRANO);
    }

    @Test
    void enUnTurnoQueCruzaLaMedianocheSeTomaLaDiferenciaMasCorta() {
        Horario noche = Horario.builder().nombre("Noche").horaInicio(LocalTime.of(22, 0))
                .horaFin(LocalTime.of(6, 0)).toleranciaMinutos(10).build();
        // 00:10 de Lima del día siguiente = 05:10Z: llegó 2 h 10 min tarde, no «21 h 50 min temprano».
        var tarde = PuntualidadEntrada.clasificar(Instant.parse("2026-11-06T05:10:00Z"), noche);
        assertThat(tarde.estado()).isEqualTo(EstadoPuntualidad.TARDE);
        assertThat(tarde.minutosRespectoAlInicio()).isEqualTo(130);
        // 21:50 de Lima = 02:50Z: llegó 10 min antes.
        var temprano = PuntualidadEntrada.clasificar(Instant.parse("2026-11-06T02:50:00Z"), noche);
        assertThat(temprano.estado()).isEqualTo(EstadoPuntualidad.TEMPRANO);
        assertThat(temprano.minutosRespectoAlInicio()).isEqualTo(-10);
    }
}
