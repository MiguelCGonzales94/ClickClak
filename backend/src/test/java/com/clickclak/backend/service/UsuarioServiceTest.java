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
import com.clickclak.backend.exception.OperacionNoPermitidaException;
import com.clickclak.backend.exception.RecursoDuplicadoException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.BitacoraAuditoria;
import com.clickclak.backend.model.EstadoCuenta;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenIntentosFallidos;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RolRepository rolRepository;
    @Mock private BitacoraAuditoriaRepository bitacoraRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AlmacenIntentosFallidos almacenIntentosFallidos;

    private UsuarioService usuarioService;

    private Rol rolColaborador;
    private Rol rolSupervisor;
    private Rol rolAdmin;

    @BeforeEach
    void configurar() {
        usuarioService = new UsuarioService(
                usuarioRepository, rolRepository, bitacoraRepository, passwordEncoder, new ObjectMapper(),
                almacenIntentosFallidos);

        rolColaborador = Rol.builder().id(1L).nombre(Rol.COLABORADOR).build();
        rolSupervisor = Rol.builder().id(2L).nombre(Rol.SUPERVISOR).build();
        rolAdmin = Rol.builder().id(3L).nombre(Rol.RRHH_ADMIN).build();
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
                "Ana", "Pérez", "DNI", "11111111", "nuevo@example.com", Rol.COLABORADOR, null);

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
                "Ana María", "Pérez", "DNI", "11111111", "ana@example.com", Rol.COLABORADOR, null);

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

    @Test
    void registrar_correoConMayusculasYEspacios_seGuardaNormalizado() {
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(any(), any())).thenReturn(false);
        when(rolRepository.findByNombre(Rol.COLABORADOR)).thenReturn(Optional.of(rolColaborador));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var resultado = usuarioService.registrarUsuario(solicitudColaborador("  Ana@Example.COM ", "12345678"), 1L);

        assertThat(resultado.correo()).isEqualTo("ana@example.com");
    }

    @Test
    void editar_correoQueSoloDifiereEnMayusculas_noCuentaComoCambio() {
        Usuario existente = usuarioExistente(5L, rolColaborador);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(rolRepository.findByNombre(Rol.COLABORADOR)).thenReturn(Optional.of(rolColaborador));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var solicitud = new EditarUsuarioRequest(
                "Ana", "Pérez", "DNI", "11111111", "ANA@example.com", Rol.COLABORADOR, null);

        var resultado = usuarioService.editarUsuario(5L, solicitud, 1L);

        assertThat(resultado.correo()).isEqualTo("ana@example.com");
        verify(usuarioRepository, never()).findByCorreo(any());
    }

    @Test
    void editar_colaboradorASupervisorSinPassword_lanzaSolicitudInvalida() {
        Usuario existente = usuarioExistente(5L, rolColaborador);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));

        var solicitud = solicitudEdicion(Rol.SUPERVISOR, null);

        assertThatThrownBy(() -> usuarioService.editarUsuario(5L, solicitud, 1L))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void editar_colaboradorASupervisorConPassword_guardaElHash() {
        Usuario existente = usuarioExistente(5L, rolColaborador);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));
        when(passwordEncoder.encode("claveSegura123")).thenReturn("hash-simulado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var resultado = usuarioService.editarUsuario(5L, solicitudEdicion(Rol.SUPERVISOR, "claveSegura123"), 1L);

        assertThat(resultado.rol()).isEqualTo(Rol.SUPERVISOR);
        assertThat(existente.getPasswordHash()).isEqualTo("hash-simulado");
    }

    @Test
    void editar_supervisorAColaborador_borraElHash() {
        Usuario existente = usuarioExistente(5L, rolSupervisor);
        existente.setPasswordHash("hash-previo");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(rolRepository.findByNombre(Rol.COLABORADOR)).thenReturn(Optional.of(rolColaborador));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        usuarioService.editarUsuario(5L, solicitudEdicion(Rol.COLABORADOR, null), 1L);

        assertThat(existente.getPasswordHash()).isNull();
    }

    @Test
    void editar_supervisorAColaboradorConPassword_lanzaSolicitudInvalida() {
        Usuario existente = usuarioExistente(5L, rolSupervisor);
        existente.setPasswordHash("hash-previo");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(rolRepository.findByNombre(Rol.COLABORADOR)).thenReturn(Optional.of(rolColaborador));

        assertThatThrownBy(() -> usuarioService.editarUsuario(5L, solicitudEdicion(Rol.COLABORADOR, "claveSegura123"), 1L))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(existente.getPasswordHash()).isEqualTo("hash-previo");
    }

    @Test
    void editar_sinCambiarDeTipoDeRolConPassword_lanzaSolicitudInvalida() {
        Usuario existente = usuarioExistente(5L, rolSupervisor);
        existente.setPasswordHash("hash-previo");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));

        assertThatThrownBy(() -> usuarioService.editarUsuario(5L, solicitudEdicion(Rol.SUPERVISOR, "claveSegura123"), 1L))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(existente.getPasswordHash()).isEqualTo("hash-previo");
    }

    @Test
    void editar_elPropioRol_lanzaOperacionNoPermitida() {
        Usuario admin = usuarioExistente(5L, rolAdmin);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));

        assertThatThrownBy(() -> usuarioService.editarUsuario(5L, solicitudEdicion(Rol.SUPERVISOR, "claveSegura123"), 5L))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void editar_bajarDeRolAlUltimoAdminActivo_lanzaOperacionNoPermitida() {
        Usuario admin = usuarioExistente(7L, rolAdmin);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));
        when(usuarioRepository.countByRolNombreAndActivoTrue(Rol.RRHH_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.editarUsuario(7L, solicitudEdicion(Rol.SUPERVISOR, "claveSegura123"), 1L))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void editar_bajarDeRolAUnAdminHabiendoOtros_esPermitido() {
        Usuario admin = usuarioExistente(7L, rolAdmin);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(rolRepository.findByNombre(Rol.SUPERVISOR)).thenReturn(Optional.of(rolSupervisor));
        when(usuarioRepository.countByRolNombreAndActivoTrue(Rol.RRHH_ADMIN)).thenReturn(2L);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var resultado = usuarioService.editarUsuario(7L, solicitudEdicion(Rol.SUPERVISOR, null), 1L);

        assertThat(resultado.rol()).isEqualTo(Rol.SUPERVISOR);
    }

    @Test
    void cambiarEstado_desactivarseASiMismo_lanzaOperacionNoPermitida() {
        Usuario admin = usuarioExistente(5L, rolAdmin);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> usuarioService.cambiarEstado(5L, false, 5L))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThat(admin.isActivo()).isTrue();
    }

    @Test
    void cambiarEstado_desactivarAlUltimoAdminActivo_lanzaOperacionNoPermitida() {
        Usuario admin = usuarioExistente(7L, rolAdmin);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRolNombreAndActivoTrue(Rol.RRHH_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarEstado(7L, false, 1L))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThat(admin.isActivo()).isTrue();
    }

    @Test
    void cambiarEstado_activar_noExigeLasProteccionesDeDesactivacion() {
        Usuario inactivo = Usuario.builder().id(5L).activo(false).rol(rolAdmin).build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(inactivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(5L)).thenReturn(inactivo);

        var resultado = usuarioService.cambiarEstado(5L, true, 5L);

        assertThat(resultado.activo()).isTrue();
    }

    @Test
    void cambiarEstado_desactivarConMotivo_guardaFechaYMotivo() {
        Usuario existente = Usuario.builder().id(5L).activo(true).rol(rolColaborador).correo("ana@example.com").build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var resultado = usuarioService.cambiarEstado(5L, false, "  Fin de contrato ", 1L);

        assertThat(resultado.estadoCuenta()).isEqualTo(EstadoCuenta.INACTIVA);
        assertThat(resultado.motivoBaja()).isEqualTo("Fin de contrato");
        assertThat(resultado.desactivadoEn()).isNotNull();
    }

    @Test
    void cambiarEstado_reactivar_limpiaFechaYMotivoDeLaBaja() {
        Usuario inactivo = Usuario.builder().id(5L).activo(false).rol(rolColaborador).correo("ana@example.com")
                .desactivadoEn(java.time.Instant.now()).motivoBaja("Fin de contrato").build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(inactivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var resultado = usuarioService.cambiarEstado(5L, true, "ignorado", 1L);

        assertThat(resultado.estadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(resultado.desactivadoEn()).isNull();
        assertThat(resultado.motivoBaja()).isNull();
    }

    @Test
    void respuesta_usuarioBloqueado_muestraEstadoBloqueada() {
        Usuario existente = Usuario.builder().id(5L).activo(true).rol(rolColaborador).correo("ana@example.com").build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(almacenIntentosFallidos.tiempoRestanteDeBloqueo("ana@example.com"))
                .thenReturn(Optional.of(java.time.Duration.ofMinutes(9)));

        assertThat(usuarioService.obtenerPorId(5L).estadoCuenta()).isEqualTo(EstadoCuenta.BLOQUEADA);
    }

    @Test
    void respuesta_claveTemporalPendiente_muestraEstadoClavePendiente() {
        Usuario existente = Usuario.builder().id(5L).activo(true).debeCambiarClave(true).rol(rolSupervisor)
                .correo("ana@example.com").build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));

        assertThat(usuarioService.obtenerPorId(5L).estadoCuenta()).isEqualTo(EstadoCuenta.CLAVE_PENDIENTE);
    }

    @Test
    void desbloquear_limpiaLosIntentosYRegistraBitacora() {
        Usuario existente = Usuario.builder().id(5L).activo(true).rol(rolColaborador).correo("ana@example.com").build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(almacenIntentosFallidos.tiempoRestanteDeBloqueo("ana@example.com"))
                .thenReturn(Optional.of(java.time.Duration.ofMinutes(9)), Optional.empty());
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        var resultado = usuarioService.desbloquear(5L, 1L);

        verify(almacenIntentosFallidos).limpiar("ana@example.com");
        verify(bitacoraRepository).save(any(BitacoraAuditoria.class));
        assertThat(resultado.estadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    void eliminar_conHistorial_lanzaOperacionNoPermitidaYNoBorra() {
        Usuario existente = usuarioExistente(5L, rolColaborador);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.tieneHistorial(5L)).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.eliminarUsuario(5L, 1L))
                .isInstanceOf(OperacionNoPermitidaException.class)
                .hasMessageContaining("desactívelo");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void eliminar_sinHistorial_borraYRegistraEliminacionEnBitacora() {
        Usuario existente = usuarioExistente(5L, rolColaborador);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.tieneHistorial(5L)).thenReturn(false);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(Usuario.builder().id(1L).build());

        usuarioService.eliminarUsuario(5L, 1L);

        verify(usuarioRepository).delete(existente);
        var captor = org.mockito.ArgumentCaptor.forClass(BitacoraAuditoria.class);
        verify(bitacoraRepository).save(captor.capture());
        assertThat(captor.getValue().getAccion()).isEqualTo(com.clickclak.backend.model.AccionAuditoria.ELIMINACION);
        assertThat(captor.getValue().getEntidadId()).isEqualTo(5L);
    }

    @Test
    void eliminar_siLaBaseRechazaElBorrado_lanzaOperacionNoPermitida() {
        Usuario existente = usuarioExistente(5L, rolColaborador);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.tieneHistorial(5L)).thenReturn(false);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("fk"))
                .when(usuarioRepository).flush();

        assertThatThrownBy(() -> usuarioService.eliminarUsuario(5L, 1L))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(bitacoraRepository, never()).save(any());
    }

    @Test
    void eliminar_laPropiaCuenta_lanzaOperacionNoPermitida() {
        Usuario admin = usuarioExistente(5L, rolAdmin);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> usuarioService.eliminarUsuario(5L, 5L))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void eliminar_alUltimoAdminActivo_lanzaOperacionNoPermitida() {
        Usuario admin = usuarioExistente(7L, rolAdmin);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRolNombreAndActivoTrue(Rol.RRHH_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.eliminarUsuario(7L, 1L))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void buscar_conTamanoFueraDeRango_lanzaSolicitudInvalida() {
        assertThatThrownBy(() -> usuarioService.buscar(null, null, null, 0, 101))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> usuarioService.buscar(null, null, null, -1, 20))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void buscar_conEstadoDesconocido_lanzaSolicitudInvalida() {
        assertThatThrownBy(() -> usuarioService.buscar(null, null, "SUSPENDIDA", 0, 20))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    private Usuario usuarioExistente(Long id, Rol rol) {
        return Usuario.builder().id(id).correo("ana@example.com").tipoDocumento("DNI")
                .numeroDocumento("11111111").rol(rol).activo(true).build();
    }

    private EditarUsuarioRequest solicitudEdicion(String rol, String password) {
        return new EditarUsuarioRequest("Ana", "Pérez", "DNI", "11111111", "ana@example.com", rol, password);
    }

    private RegistrarUsuarioRequest solicitudColaborador(String correo, String documento) {
        return new RegistrarUsuarioRequest("Ana", "Pérez", "DNI", documento, correo, Rol.COLABORADOR, null);
    }
}
