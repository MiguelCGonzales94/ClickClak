package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GeneradorClaveTemporalTest {

    @Test
    void laClaveGeneradaCumpleLaPoliticaDeContrasenas() {
        for (int i = 0; i < 200; i++) {
            String clave = GeneradorClaveTemporal.generar();
            assertThatCode(() -> PoliticaContrasenas.validar(clave)).doesNotThrowAnyException();
        }
    }

    @Test
    void tieneDoceCaracteresConLetrasYDigitosSinAmbiguos() {
        String clave = GeneradorClaveTemporal.generar();

        assertThat(clave).hasSize(GeneradorClaveTemporal.LETRAS + GeneradorClaveTemporal.DIGITOS);
        assertThat(clave.chars().filter(Character::isDigit).count()).isEqualTo(GeneradorClaveTemporal.DIGITOS);
        assertThat(clave).doesNotContainPattern("[0O1lI]");
    }

    @Test
    void cadaLlamadaDevuelveUnaClaveDistinta() {
        Set<String> claves = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            claves.add(GeneradorClaveTemporal.generar());
        }

        assertThat(claves).hasSize(100);
    }
}
