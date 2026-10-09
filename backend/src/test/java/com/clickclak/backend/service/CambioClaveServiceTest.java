package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.clickclak.backend.dto.CambiarClaveRequest;
import com.clickclak.backend.exception.DemasiadosIntentosException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenIntentosFallidos;

@ExtendWith(MockitoExtension.class)
class CambioClaveServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AlmacenIntentosFallidos almacenIntentosFallidos;
    @Mock private AutenticacionService autenticacionService;
    @Mock private AuditoriaService auditoriaService;

    private CambioClaveService servicio;
    private Usuario supervisor;

    @BeforeEach
    void configurar() {
        servicio = new CambioClaveService(
                usuarioRepository, passwordEncoder, almacenIntentosFallidos, autenticacionService, auditoriaService);
        supervisor = Usuario.builder().id(5L).correo("ana@example.com").activo(true).debeCambiarClave(true)
                .passwordHash("hash-actual").rol(Rol.builder().nombre(Rol.SUPERVISOR).build()).build();
    }

    @Test
    void cambioCorrecto_guardaHashNuevoLimpiaLaMarcaRevocaElTokenYAudita() {
        when(usuarioRepository.findConRolById(5L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.matches("Temporal2468", "hash-actual")).thenReturn(true);
        when(passwordEncoder.matches("Nueva12345", "hash-actual")).thenReturn(false);
        when(passwordEncoder.encode("Nueva12345")).thenReturn("hash-nuevo");

        var respuesta = servicio.cambiarClave(5L, "token-jwt", new CambiarClaveRequest("Temporal2468", "Nueva12345"));

        assertThat(supervisor.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(supervisor.isDebeCambiarClave()).isFalse();
        assertThat(respuesta.mensaje()).contains("Inicia sesión de nuevo");
        verify(autenticacionService).cerrarSesion("token-jwt");
        verify(almacenIntentosFallidos).limpiar("ana@example.com");
        verify(auditoriaService).registrar(
                eq(supervisor), eq("usuario"), eq(5L), eq(AccionAuditoria.MODIFICACION), any(), any());
    }

    @Test
    void claveActualIncorrecta_cuentaComoFalloYNoCambiaNada() {
        when(usuarioRepository.findConRolById(5L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.matches("equivocada1", "hash-actual")).thenReturn(false);

        assertThatThrownBy(() -> servicio.cambiarClave(5L, "t", new CambiarClaveRequest("equivocada1", "Nueva12345")))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("actual no es correcta");

        verify(almacenIntentosFallidos).registrarFallo("ana@example.com");
        verify(usuarioRepository, never()).save(any());
        verify(autenticacionService, never()).cerrarSesion(any());
        assertThat(supervisor.getPasswordHash()).isEqualTo("hash-actual");
    }

    @Test
    void claveNuevaDebil_seRechazaSinGuardar() {
        when(usuarioRepository.findConRolById(5L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.matches("Temporal2468", "hash-actual")).thenReturn(true);

        assertThatThrownBy(() -> servicio.cambiarClave(5L, "t", new CambiarClaveRequest("Temporal2468", "corta")))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void claveNuevaIgualALaActual_seRechaza() {
        when(usuarioRepository.findConRolById(5L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.matches("Temporal2468", "hash-actual")).thenReturn(true);

        assertThatThrownBy(() -> servicio.cambiarClave(5L, "t", new CambiarClaveRequest("Temporal2468", "Temporal2468")))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("distinta");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cuentaBloqueada_respondeDemasiadosIntentosSinMirarLaClave() {
        when(usuarioRepository.findConRolById(5L)).thenReturn(Optional.of(supervisor));
        when(almacenIntentosFallidos.tiempoRestanteDeBloqueo("ana@example.com"))
                .thenReturn(Optional.of(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> servicio.cambiarClave(5L, "t", new CambiarClaveRequest("Temporal2468", "Nueva12345")))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void colaborador_noTieneContrasenaQueCambiar() {
        supervisor.setRol(Rol.builder().nombre(Rol.COLABORADOR).build());
        supervisor.setPasswordHash(null);
        when(usuarioRepository.findConRolById(5L)).thenReturn(Optional.of(supervisor));

        assertThatThrownBy(() -> servicio.cambiarClave(5L, "t", new CambiarClaveRequest("x1234567", "Nueva12345")))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("WebAuthn");
    }

    @Test
    void usuarioInactivo_seTrataComoNoEncontrado() {
        supervisor.setActivo(false);
        when(usuarioRepository.findConRolById(5L)).thenReturn(Optional.of(supervisor));

        assertThatThrownBy(() -> servicio.cambiarClave(5L, "t", new CambiarClaveRequest("Temporal2468", "Nueva12345")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
