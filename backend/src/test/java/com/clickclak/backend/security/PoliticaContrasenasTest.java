package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.clickclak.backend.exception.SolicitudInvalidaException;

class PoliticaContrasenasTest {

    @Test
    void claveValida_noLanzaExcepcion() {
        assertThatCode(() -> PoliticaContrasenas.validar("clave1234")).doesNotThrowAnyException();
    }

    @Test
    void claveCorta_lanzaExcepcion() {
        assertThatThrownBy(() -> PoliticaContrasenas.validar("abc123"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void claveSinNumeros_lanzaExcepcion() {
        assertThatThrownBy(() -> PoliticaContrasenas.validar("soloLetras"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void claveSinLetras_lanzaExcepcion() {
        assertThatThrownBy(() -> PoliticaContrasenas.validar("12345678"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void claveNula_lanzaExcepcion() {
        assertThatThrownBy(() -> PoliticaContrasenas.validar(null))
                .isInstanceOf(SolicitudInvalidaException.class);
    }
}
