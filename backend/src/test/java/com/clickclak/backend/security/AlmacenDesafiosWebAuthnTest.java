package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AlmacenDesafiosWebAuthnTest {

    @Test
    void guardarYRecuperar_devuelveElMismoValor() {
        var almacen = new AlmacenDesafiosWebAuthn();
        String id = almacen.guardar("desafio-de-prueba");

        var recuperado = almacen.recuperar(id, String.class);

        assertThat(recuperado).contains("desafio-de-prueba");
    }

    @Test
    void recuperar_esDeUnSoloUso() {
        var almacen = new AlmacenDesafiosWebAuthn();
        String id = almacen.guardar("desafio-de-prueba");

        almacen.recuperar(id, String.class);
        var segundaLectura = almacen.recuperar(id, String.class);

        assertThat(segundaLectura).isEmpty();
    }

    @Test
    void recuperar_conIdDesconocido_devuelveVacio() {
        var almacen = new AlmacenDesafiosWebAuthn();

        assertThat(almacen.recuperar("id-que-no-existe", String.class)).isEmpty();
    }

    @Test
    void recuperar_conTipoIncorrecto_devuelveVacio() {
        var almacen = new AlmacenDesafiosWebAuthn();
        String id = almacen.guardar("un-string");

        assertThat(almacen.recuperar(id, Integer.class)).isEmpty();
    }

    @Test
    void recuperar_despuesDeExpirar_devuelveVacio() throws InterruptedException {
        var almacen = new AlmacenDesafiosWebAuthn(Duration.ofMillis(1));
        String id = almacen.guardar("desafio-efimero");

        Thread.sleep(20);

        assertThat(almacen.recuperar(id, String.class)).isEmpty();
    }
}
