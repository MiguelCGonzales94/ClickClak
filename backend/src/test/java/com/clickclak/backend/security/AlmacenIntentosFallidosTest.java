package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlmacenIntentosFallidosTest {

    /** Reloj que se mueve a mano, para probar la ventana de 15 minutos sin esperar. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-10-03T12:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zona) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private RelojManual reloj;
    private AlmacenIntentosFallidos almacen;

    @BeforeEach
    void configurar() {
        reloj = new RelojManual();
        almacen = new AlmacenIntentosFallidos(reloj);
    }

    @Test
    void conMenosDeCincoFallos_noHayBloqueo() {
        for (int i = 0; i < 4; i++) {
            almacen.registrarFallo("ana@example.com");
        }

        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).isEmpty();
    }

    @Test
    void alQuintoFallo_bloqueaQuinceMinutos() {
        for (int i = 0; i < 5; i++) {
            almacen.registrarFallo("ana@example.com");
        }

        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).contains(Duration.ofMinutes(15));
    }

    @Test
    void elBloqueoVenceConElTiempo() {
        for (int i = 0; i < 5; i++) {
            almacen.registrarFallo("ana@example.com");
        }

        reloj.avanzar(Duration.ofMinutes(14));
        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).contains(Duration.ofMinutes(1));

        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).isEmpty();
    }

    @Test
    void trasElVencimiento_losFallosEmpiezanDeCero() {
        for (int i = 0; i < 5; i++) {
            almacen.registrarFallo("ana@example.com");
        }
        reloj.avanzar(Duration.ofMinutes(16));

        for (int i = 0; i < 4; i++) {
            almacen.registrarFallo("ana@example.com");
        }

        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).isEmpty();
    }

    @Test
    void losFallosViejosFueraDeLaVentanaNoSeAcumulan() {
        for (int i = 0; i < 4; i++) {
            almacen.registrarFallo("ana@example.com");
        }
        reloj.avanzar(Duration.ofMinutes(20));
        almacen.registrarFallo("ana@example.com");

        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).isEmpty();
    }

    @Test
    void limpiarBorraElHistorialDeLaCuenta() {
        for (int i = 0; i < 5; i++) {
            almacen.registrarFallo("ana@example.com");
        }

        almacen.limpiar("ana@example.com");

        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).isEmpty();
    }

    @Test
    void cadaCuentaTieneSuPropioContador() {
        for (int i = 0; i < 5; i++) {
            almacen.registrarFallo("ana@example.com");
        }

        assertThat(almacen.tiempoRestanteDeBloqueo("luis@example.com")).isEmpty();
    }

    @Test
    void ignoraMayusculasYEspaciosEnElCorreo() {
        for (int i = 0; i < 5; i++) {
            almacen.registrarFallo("  Ana@Example.COM ");
        }

        assertThat(almacen.tiempoRestanteDeBloqueo("ana@example.com")).isPresent();
    }

    @Test
    void conMuchasCuentasNuevas_noCreceSinLimite() {
        for (int i = 0; i < AlmacenIntentosFallidos.MAXIMO_ENTRADAS + 500; i++) {
            almacen.registrarFallo("cuenta" + i + "@example.com");
        }
        // Una cuenta nueva ya no se rastrea, pero tampoco rompe nada...
        almacen.registrarFallo("extra@example.com");
        assertThat(almacen.tiempoRestanteDeBloqueo("extra@example.com")).isEmpty();

        // ...y una cuenta ya rastreada sigue pudiendo bloquearse.
        for (int i = 0; i < 4; i++) {
            almacen.registrarFallo("cuenta0@example.com");
        }
        assertThat(almacen.tiempoRestanteDeBloqueo("cuenta0@example.com")).isPresent();
    }
}
