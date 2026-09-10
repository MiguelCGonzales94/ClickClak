package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.clickclak.backend.dto.EditarUsuarioRequest;
import com.clickclak.backend.dto.RegistrarUsuarioRequest;
import com.clickclak.backend.exception.RecursoDuplicadoException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.BitacoraAuditoria;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RolRepository rolRepository;
    @Mock private BitacoraAuditoriaRepository bitacoraRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService;

    private Rol rolColaborador;
    private Rol rolSupervisor;

    @BeforeEach
    void configurar() {
        usuarioService = new UsuarioService(
                usuarioRepository, rolRepository, bitacoraRepository, passwordEncoder, new ObjectMapper());

        rolColaborador = Rol.builder().id(1L).nombre(Rol.COLABORADOR).build();
        rolSupervisor = Rol.builder().id(2L).nombre(Rol.SUPERVISOR).build();
    }

    @Test
    void registrar_conCorreoYaExistente_lanzaRecursoDuplicado() {
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(Usuario.builder().build()));

        var solicitud = solicitudColaborador("ana@example.com", "12345678");

        assertThatThrownBy(() -> usuarioService.registrarUsuario(solicitud, 1L))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    void registrar_conDocumentoYaExistente_lanzaRecursoDuplicado() {
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento("DNI", "12345678")).thenReturn(true);

        var solicitud = solicitudColaborador("ana@example.com", "12345678");

        assertThatThrownBy(() -> usuarioService.registrarUsuario(solicitud, 1L))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    void registrar_conRolInexistente_lanzaRecursoNoEncontrado() {
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(any(), any())).thenReturn(false);
        when(rolRepository.findByNombre("INEXISTENTE")).thenReturn(Optional.empty());

        var solicitud = new RegistrarUsuarioRequest(
                "Ana", "Pérez", "DNI", "12345678", "ana@example.com", "INEXISTENTE", null);

        assertThatThrownBy(() -> usuarioService.registrarUsuario(solicitud, 1L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void registrar_colaboradorConPassword_lanzaSolicitudInvalida() {
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(any(), any())).thenReturn(false);
        when(rolRepository.findByNombre(Rol.COLABORADOR)).thenReturn(Optional.of(rolColaborador));

        var solicitud = new RegistrarUsuarioRequest(
                "Ana", "Pérez", "DNI", "12345678", "ana@example.com", Rol.COLABORADOR, "unaClave123");

        assertThatThrownBy(() -> usuarioService.registrarUsuario(solicitud, 1L))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void registrar_supervisorSinPassword_lanzaSolicitudInvalida() {
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(any(), any())).thenReturn(false);
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));

        var solicitud = new RegistrarUsuarioRequest(
                "Ana", "Pérez", "DNI", "12345678", "ana@example.com", Rol.SUPERVISOR, null);

        assertThatThrownBy(() -> usuarioService.registrarUsuario(solicitud, 1L))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void registrar_colaboradorSinPassword_seCreaSinHashYQuedaEnBitacora() {
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(any(), any())).thenReturn(false);
        when(rolRepository.findByNombre(Rol.COLABORADOR)).thenReturn(Optional.of(rolColaborador));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var solicitud = solicitudColaborador("ana@example.com", "12345678");

        var resultado = usuarioService.registrarUsuario(solicitud, 1L);

        assertThat(resultado.id()).isEqualTo(10L);
        assertThat(resultado.rol()).isEqualTo(Rol.COLABORADOR);
        verify(passwordEncoder, never()).encode(any());
        verify(bitacoraRepository).save(any(BitacoraAuditoria.class));
    }

    @Test
    void registrar_supervisorConPassword_encriptaLaClave() {
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(any(), any())).thenReturn(false);
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));
        when(passwordEncoder.encode("claveSegura123")).thenReturn("hash-simulado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var solicitud = new RegistrarUsuarioRequest(
                "Ana", "Pérez", "DNI", "12345678", "ana@example.com", Rol.SUPERVISOR, "claveSegura123");

        usuarioService.registrarUsuario(solicitud, 1L);

        verify(passwordEncoder).encode("claveSegura123");
    }

    @Test
    void editar_cambiandoACorreoDeOtroUsuario_lanzaRecursoDuplicado() {
        Usuario existente = Usuario.builder().id(5L).correo("viejo@example.com")
                .tipoDocumento("DNI").numeroDocumento("11111111").rol(rolColaborador).activo(true).build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByCorreo("nuevo@example.com")).thenReturn(Optional.of(Usuario.builder().id(99L).build()));

        var solicitud = new EditarUsuarioRequest(
                "Ana", "Pérez", "DNI", "11111111", "nuevo@example.com", Rol.COLABORADOR);

        assertThatThrownBy(() -> usuarioService.editarUsuario(5L, solicitud, 1L))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    @Test
    void editar_sinCambiarCorreoNiDocumento_noValidaDuplicados() {
        Usuario existente = Usuario.builder().id(5L).correo("ana@example.com")
                .tipoDocumento("DNI").numeroDocumento("11111111").rol(rolColaborador).activo(true).build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(rolRepository.findByNombre(Rol.COLABORADOR)).thenReturn(Optional.of(rolColaborador));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var solicitud = new EditarUsuarioRequest(
                "Ana María", "Pérez", "DNI", "11111111", "ana@example.com", Rol.COLABORADOR);

        var resultado = usuarioService.editarUsuario(5L, solicitud, 1L);

        assertThat(resultado.nombres()).isEqualTo("Ana María");
        verify(usuarioRepository, never()).findByCorreo("ana@example.com");
    }

    @Test
    void cambiarEstado_desactivar_actualizaYRegistraBitacora() {
        Usuario existente = Usuario.builder().id(5L).activo(true).rol(rolColaborador).build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var resultado = usuarioService.cambiarEstado(5L, false, 1L);

        assertThat(resultado.activo()).isFalse();
        verify(bitacoraRepository).save(any(BitacoraAuditoria.class));
    }

    private RegistrarUsuarioRequest solicitudColaborador(String correo, String documento) {
        return new RegistrarUsuarioRequest("Ana", "Pérez", "DNI", documento, correo, Rol.COLABORADOR, null);
    }
}
