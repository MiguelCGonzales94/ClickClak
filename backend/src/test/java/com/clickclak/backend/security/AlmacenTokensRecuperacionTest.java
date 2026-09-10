package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlmacenTokensRecuperacionTest {

    @Test
    void generarYConsumir_devuelveElUsuarioCorrecto() {
        var almacen = new AlmacenTokensRecuperacion();

        String token = almacen.generar(42L);

        assertThat(almacen.consumir(token)).contains(42L);
    }

    @Test
    void consumir_esDeUnSoloUso() {
        var almacen = new AlmacenTokensRecuperacion();
        String token = almacen.generar(42L);

        almacen.consumir(token);

        assertThat(almacen.consumir(token)).isEmpty();
    }

    @Test
    void consumir_conTokenDesconocido_devuelveVacio() {
        var almacen = new AlmacenTokensRecuperacion();

        assertThat(almacen.consumir("token-inexistente")).isEmpty();
    }
}
