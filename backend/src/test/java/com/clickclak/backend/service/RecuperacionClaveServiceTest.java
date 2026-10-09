package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.clickclak.backend.dto.RestablecerClaveRequest;
import com.clickclak.backend.dto.SolicitarRecuperacionRequest;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenTokensRecuperacion;

@ExtendWith(MockitoExtension.class)
class RecuperacionClaveServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificadorRecuperacion notificador;
    @Mock private PasswordEncoder passwordEncoder;

    private AlmacenTokensRecuperacion almacenTokens;
    private RecuperacionClaveService servicio;

    @BeforeEach
    void configurar() {
        almacenTokens = new AlmacenTokensRecuperacion();
        servicio = new RecuperacionClaveService(usuarioRepository, almacenTokens, notificador, passwordEncoder);
    }

    @Test
    void solicitar_conCorreoDeSupervisor_generaTokenYNotifica() {
        Usuario supervisor = Usuario.builder().id(1L).correo("ana@example.com")
                .passwordHash("hash-actual").activo(true).build();
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(supervisor));

        servicio.solicitar(new SolicitarRecuperacionRequest("ana@example.com"));

        verify(notificador).enviarEnlaceRecuperacion(org.mockito.ArgumentMatchers.eq("ana@example.com"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void solicitar_conCorreoInexistente_noNotificaPeroNoFalla() {
        when(usuarioRepository.findByCorreo("nadie@example.com")).thenReturn(Optional.empty());

        var respuesta = servicio.solicitar(new SolicitarRecuperacionRequest("nadie@example.com"));

        assertThat(respuesta.mensaje()).isNotBlank();
        verify(notificador, never()).enviarEnlaceRecuperacion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void solicitar_conColaboradorSinPassword_noNotifica() {
        Usuario colaborador = Usuario.builder().id(2L).correo("tecnico@example.com")
                .passwordHash(null).activo(true).build();
        when(usuarioRepository.findByCorreo("tecnico@example.com")).thenReturn(Optional.of(colaborador));

        servicio.solicitar(new SolicitarRecuperacionRequest("tecnico@example.com"));

        verify(notificador, never()).enviarEnlaceRecuperacion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restablecer_conTokenDesconocido_lanzaSolicitudInvalida() {
        var solicitud = new RestablecerClaveRequest("token-que-no-existe", "claveNueva123");

        assertThatThrownBy(() -> servicio.restablecer(solicitud)).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void restablecer_conClaveQueNoCumpleLaPolitica_lanzaSolicitudInvalidaYNoGuarda() {
        Usuario supervisor = Usuario.builder().id(1L).correo("ana@example.com").activo(true)
                .rol(Rol.builder().nombre(Rol.SUPERVISOR).build()).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(supervisor));
        String token = almacenTokens.generar(1L);

        assertThatThrownBy(() -> servicio.restablecer(new RestablecerClaveRequest(token, "corta")))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restablecer_conUsuarioColaborador_lanzaSolicitudInvalida() {
        Usuario colaborador = Usuario.builder().id(2L).correo("tecnico@example.com").activo(true)
                .rol(Rol.builder().nombre(Rol.COLABORADOR).build()).build();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(colaborador));
        String token = almacenTokens.generar(2L);

        assertThatThrownBy(() -> servicio.restablecer(new RestablecerClaveRequest(token, "claveNueva123")))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void restablecer_conDatosValidos_actualizaLaClaveYConsumeElToken() {
        Usuario supervisor = Usuario.builder().id(1L).correo("ana@example.com").activo(true)
                .rol(Rol.builder().nombre(Rol.SUPERVISOR).build()).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.encode("claveNueva123")).thenReturn("hash-nuevo");
        String token = almacenTokens.generar(1L);

        servicio.restablecer(new RestablecerClaveRequest(token, "claveNueva123"));

        assertThat(supervisor.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(almacenTokens.consumir(token)).isEmpty();
    }

    @Test
    void restablecer_conClaveTemporalPendiente_laMarcaDejaDeAplicar() {
        Usuario supervisor = Usuario.builder().id(1L).correo("ana@example.com").activo(true).debeCambiarClave(true)
                .rol(Rol.builder().nombre(Rol.SUPERVISOR).build()).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.encode("claveNueva123")).thenReturn("hash-nuevo");
        String token = almacenTokens.generar(1L);

        servicio.restablecer(new RestablecerClaveRequest(token, "claveNueva123"));

        assertThat(supervisor.isDebeCambiarClave()).isFalse();
    }
}
