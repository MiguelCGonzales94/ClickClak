package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRETO_PRUEBA = "clave-de-prueba-suficientemente-larga-para-hmac-sha256";

    private final JwtService jwtService = new JwtService(SECRETO_PRUEBA, 60);

    @Test
    void generaUnTokenQueSePuedeValidarYContieneElRol() {
        Usuario usuario = Usuario.builder()
                .id(42L)
                .correo("supervisor@example.com")
                .rol(Rol.builder().nombre(Rol.SUPERVISOR).build())
                .build();

        String token = jwtService.generarToken(usuario);
        var claims = jwtService.validar(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("rol", String.class)).isEqualTo(Rol.SUPERVISOR);
    }

    @Test
    void unTokenFirmadoConOtraClave_esRechazado() {
        Usuario usuario = Usuario.builder().id(1L)
                .rol(Rol.builder().nombre(Rol.COLABORADOR).build()).build();
        String token = jwtService.generarToken(usuario);

        JwtService otroServicio = new JwtService("otra-clave-completamente-distinta-de-la-original-123", 60);

        assertThatThrownBy(() -> otroServicio.validar(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenManipulado_esRechazado() {
        Usuario usuario = Usuario.builder().id(1L)
                .rol(Rol.builder().nombre(Rol.COLABORADOR).build()).build();
        String token = jwtService.generarToken(usuario);
        String tokenAlterado = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.validar(tokenAlterado)).isInstanceOf(JwtException.class);
    }
}
