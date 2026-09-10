package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AlmacenTokensRevocadosTest {

    @Test
    void tokenNoRevocado_noApareceComoRevocado() {
        var almacen = new AlmacenTokensRevocados();

        assertThat(almacen.estaRevocado("cualquier-token")).isFalse();
    }

    @Test
    void tokenRevocado_apareceComoRevocado() {
        var almacen = new AlmacenTokensRevocados();

        almacen.revocar("token-1", Instant.now().plusSeconds(60));

        assertThat(almacen.estaRevocado("token-1")).isTrue();
        assertThat(almacen.estaRevocado("token-2")).isFalse();
    }
}
