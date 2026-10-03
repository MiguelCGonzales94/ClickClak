package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.clickclak.backend.dto.IncidenciaResponse;
import com.clickclak.backend.dto.RegistrarIncidenciaRequest;
import com.clickclak.backend.exception.RecursoDuplicadoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.exception.TransicionIncidenciaInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.EstadoIncidencia;
import com.clickclak.backend.model.Incidencia;
import com.clickclak.backend.model.IncidenciaHistorial;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.TipoIncidencia;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.IncidenciaHistorialRepository;
import com.clickclak.backend.repository.IncidenciaRepository;
import com.clickclak.backend.repository.MarcacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class IncidenciaServiceTest {

    private static final LocalDate AYER = LocalDate.now(ZoneId.of("America/Lima")).minusDays(1);

    @Mock private IncidenciaRepository incidenciaRepository;
    @Mock private IncidenciaHistorialRepository historialRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MarcacionRepository marcacionRepository;
    @Mock private AuditoriaService auditoriaService;

    private IncidenciaService incidenciaService;

    private Usuario colaborador;
    private Usuario otroColaborador;
    private Usuario supervisor;
    private Usuario rrhh;

    @BeforeEach
    void configurar() {
        incidenciaService = new IncidenciaService(
                incidenciaRepository, historialRepository, usuarioRepository, marcacionRepository, auditoriaService);

        colaborador = usuario(1L, "Ana", Rol.COLABORADOR);
        otroColaborador = usuario(2L, "Luis", Rol.COLABORADOR);
        supervisor = usuario(3L, "Sara", Rol.SUPERVISOR);
        rrhh = usuario(4L, "Raúl", Rol.RRHH_ADMIN);
        for (Usuario u : List.of(colaborador, otroColaborador, supervisor, rrhh)) {
            org.mockito.Mockito.lenient().when(usuarioRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
        org.mockito.Mockito.lenient().when(incidenciaRepository.save(any(Incidencia.class))).thenAnswer(invocacion -> {
            Incidencia guardada = invocacion.getArgument(0);
            if (guardada.getId() == null) {
                guardada.setId(100L);
            }
            return guardada;
        });
    }

    // --- registro ---------------------------------------------------------------------------

    @Test
    void colaboradorRegistraLaPropia_quedaRegistradaConHistorialYAuditoria() {
        IncidenciaResponse respuesta = incidenciaService.registrar(colaborador.getId(),
                solicitud(null, TipoIncidencia.AUSENCIA, AYER));

        assertThat(respuesta.estado()).isEqualTo(EstadoIncidencia.REGISTRADA);
        assertThat(respuesta.usuarioId()).isEqualTo(colaborador.getId());
        assertThat(respuesta.creadoPorId()).isEqualTo(colaborador.getId());

        ArgumentCaptor<IncidenciaHistorial> historial = ArgumentCaptor.forClass(IncidenciaHistorial.class);
        verify(historialRepository).save(historial.capture());
        assertThat(historial.getValue().getEstadoAnterior()).isNull();
        assertThat(historial.getValue().getEstadoNuevo()).isEqualTo(EstadoIncidencia.REGISTRADA);
        verify(auditoriaService).registrar(eq(colaborador), eq("incidencia"), eq(100L),
                eq(AccionAuditoria.CREACION), eq(null), any());
    }

    @Test
    void colaboradorNoPuedeRegistrarANombreDeOtro() {
        assertThatThrownBy(() -> incidenciaService.registrar(colaborador.getId(),
                solicitud(otroColaborador.getId(), TipoIncidencia.AUSENCIA, AYER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(incidenciaRepository, never()).save(any());
    }

    @Test
    void supervisorRegistraANombreDeUnColaborador() {
        IncidenciaResponse respuesta = incidenciaService.registrar(supervisor.getId(),
                solicitud(colaborador.getId(), TipoIncidencia.OLVIDO_REGISTRO, AYER));

        assertThat(respuesta.usuarioId()).isEqualTo(colaborador.getId());
        assertThat(respuesta.creadoPorId()).isEqualTo(supervisor.getId());
    }

    @Test
    void supervisorDebeIndicarElColaborador() {
        assertThatThrownBy(() -> incidenciaService.registrar(supervisor.getId(),
                solicitud(null, TipoIncidencia.AUSENCIA, AYER)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("colaborador");
    }

    @Test
    void noRegistraAColaboradorInactivo() {
        colaborador.setActivo(false);

        assertThatThrownBy(() -> incidenciaService.registrar(supervisor.getId(),
                solicitud(colaborador.getId(), TipoIncidencia.AUSENCIA, AYER)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    void fechaFuturaSoloSeAceptaParaPermisos() {
        LocalDate manana = AYER.plusDays(2);

        assertThatThrownBy(() -> incidenciaService.registrar(colaborador.getId(),
                solicitud(null, TipoIncidencia.TARDANZA, manana)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("futura");

        IncidenciaResponse permiso = incidenciaService.registrar(colaborador.getId(),
                solicitud(null, TipoIncidencia.PERMISO, manana));
        assertThat(permiso.tipo()).isEqualTo(TipoIncidencia.PERMISO);
    }

    @Test
    void rechazaMarcacionQueNoEsDelColaborador() {
        Marcacion ajena = Marcacion.builder().id(55L).usuario(otroColaborador).build();
        when(marcacionRepository.findById(55L)).thenReturn(Optional.of(ajena));
        RegistrarIncidenciaRequest solicitud = new RegistrarIncidenciaRequest(
                null, 55L, TipoIncidencia.TARDANZA, AYER, "Llegué tarde");

        assertThatThrownBy(() -> incidenciaService.registrar(colaborador.getId(), solicitud))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("marcación");
    }

    @Test
    void rechazaDuplicadoMientrasLaPrimeraSigaAbierta() {
        when(incidenciaRepository.existsByUsuarioIdAndTipoAndFechaEventoAndEstadoIn(
                eq(colaborador.getId()), eq(TipoIncidencia.AUSENCIA), eq(AYER), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> incidenciaService.registrar(colaborador.getId(),
                solicitud(null, TipoIncidencia.AUSENCIA, AYER)))
                .isInstanceOf(RecursoDuplicadoException.class);
    }

    // --- flujo de revisión ------------------------------------------------------------------

    @Test
    void flujoCompleto_registradaARevisionAprobadaYCerrada() {
        Incidencia incidencia = incidencia(EstadoIncidencia.REGISTRADA, colaborador, colaborador);

        assertThat(incidenciaService.iniciarRevision(supervisor.getId(), 100L, null).estado())
                .isEqualTo(EstadoIncidencia.EN_REVISION);
        IncidenciaResponse aprobada = incidenciaService.aprobar(supervisor.getId(), 100L, "Sustento válido");
        assertThat(aprobada.estado()).isEqualTo(EstadoIncidencia.APROBADA);
        assertThat(aprobada.revisadoPorId()).isEqualTo(supervisor.getId());
        assertThat(aprobada.revisadoEn()).isNotNull();
        assertThat(aprobada.comentarioRevision()).isEqualTo("Sustento válido");
        assertThat(incidenciaService.cerrar(rrhh.getId(), 100L, null).estado())
                .isEqualTo(EstadoIncidencia.CERRADA);

        assertThat(incidencia.getEstado()).isEqualTo(EstadoIncidencia.CERRADA);
        verify(historialRepository, org.mockito.Mockito.times(3)).save(any(IncidenciaHistorial.class));
        verify(auditoriaService, org.mockito.Mockito.times(3)).registrar(
                any(), eq("incidencia"), eq(100L), eq(AccionAuditoria.MODIFICACION), any(), any());
    }

    @Test
    void rechazarExigeMotivo() {
        incidencia(EstadoIncidencia.EN_REVISION, colaborador, colaborador);

        assertThatThrownBy(() -> incidenciaService.rechazar(supervisor.getId(), 100L, "  "))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void rechazadaConMotivo_guardaQuienYCuando() {
        incidencia(EstadoIncidencia.EN_REVISION, colaborador, colaborador);

        IncidenciaResponse rechazada = incidenciaService.rechazar(supervisor.getId(), 100L, " Sin sustento ");

        assertThat(rechazada.estado()).isEqualTo(EstadoIncidencia.RECHAZADA);
        assertThat(rechazada.comentarioRevision()).isEqualTo("Sin sustento");
        assertThat(rechazada.revisadoPorId()).isEqualTo(supervisor.getId());
    }

    @Test
    void noSePuedeSaltarElFlujo() {
        incidencia(EstadoIncidencia.REGISTRADA, colaborador, colaborador);

        assertThatThrownBy(() -> incidenciaService.aprobar(supervisor.getId(), 100L, null))
                .isInstanceOf(TransicionIncidenciaInvalidaException.class)
                .hasMessageContaining("REGISTRADA");
        assertThatThrownBy(() -> incidenciaService.cerrar(supervisor.getId(), 100L, null))
                .isInstanceOf(TransicionIncidenciaInvalidaException.class);
    }

    @Test
    void unaIncidenciaCerradaNoVuelveAcambiar() {
        incidencia(EstadoIncidencia.CERRADA, colaborador, colaborador);

        assertThatThrownBy(() -> incidenciaService.iniciarRevision(supervisor.getId(), 100L, null))
                .isInstanceOf(TransicionIncidenciaInvalidaException.class);
        assertThatThrownBy(() -> incidenciaService.cerrar(supervisor.getId(), 100L, null))
                .isInstanceOf(TransicionIncidenciaInvalidaException.class);
    }

    @Test
    void unaIncidenciaAprobadaNoSePuedeRechazar() {
        incidencia(EstadoIncidencia.APROBADA, colaborador, colaborador);

        assertThatThrownBy(() -> incidenciaService.rechazar(supervisor.getId(), 100L, "Motivo"))
                .isInstanceOf(TransicionIncidenciaInvalidaException.class);
    }

    @Test
    void soloElPersonalDeRevisionCambiaEstados() {
        incidencia(EstadoIncidencia.REGISTRADA, colaborador, colaborador);

        assertThatThrownBy(() -> incidenciaService.iniciarRevision(colaborador.getId(), 100L, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> incidenciaService.aprobar(otroColaborador.getId(), 100L, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nadieRevisaUnaIncidenciaQueLeAfecta() {
        // Un supervisor también puede tener incidencias propias (llegó tarde, faltó…).
        incidencia(EstadoIncidencia.REGISTRADA, supervisor, supervisor);

        assertThatThrownBy(() -> incidenciaService.iniciarRevision(supervisor.getId(), 100L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("revisar");
    }

    @Test
    void quienRegistroANombreDeOtroNoLaRevisa() {
        incidencia(EstadoIncidencia.EN_REVISION, colaborador, supervisor);

        assertThatThrownBy(() -> incidenciaService.aprobar(supervisor.getId(), 100L, null))
                .isInstanceOf(AccessDeniedException.class);

        // Otro integrante del personal sí puede resolverla.
        assertThat(incidenciaService.aprobar(rrhh.getId(), 100L, null).estado())
                .isEqualTo(EstadoIncidencia.APROBADA);
    }

    // --- consulta ---------------------------------------------------------------------------

    @Test
    void colaboradorNoConsultaLaIncidenciaDeOtro() {
        Incidencia ajena = Incidencia.builder().id(100L).usuario(otroColaborador).creadoPor(otroColaborador)
                .tipo(TipoIncidencia.AUSENCIA).estado(EstadoIncidencia.REGISTRADA).fechaEvento(AYER).build();
        when(incidenciaRepository.findById(100L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> incidenciaService.obtener(colaborador.getId(), 100L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void supervisorSiConsultaLaIncidenciaDeUnColaborador() {
        Incidencia propia = Incidencia.builder().id(100L).usuario(colaborador).creadoPor(colaborador)
                .tipo(TipoIncidencia.AUSENCIA).estado(EstadoIncidencia.REGISTRADA).fechaEvento(AYER).build();
        when(incidenciaRepository.findById(100L)).thenReturn(Optional.of(propia));
        when(historialRepository.trazaDe(100L)).thenReturn(List.of());

        assertThat(incidenciaService.obtener(supervisor.getId(), 100L).incidencia().usuarioId())
                .isEqualTo(colaborador.getId());
    }

    // --- alta automática --------------------------------------------------------------------

    @Test
    void altaAutomatica_nacerConHistorialYAuditoriaDelSistema() {
        Incidencia creada = incidenciaService.registrarAutomatica(
                colaborador, null, TipoIncidencia.TARDANZA, AYER, "Generada por el motor");

        assertThat(creada.getEstado()).isEqualTo(EstadoIncidencia.REGISTRADA);
        assertThat(creada.getCreadoPor()).isSameAs(colaborador);
        verify(historialRepository).save(any(IncidenciaHistorial.class));
        // actor nulo = el sistema, no una persona
        verify(auditoriaService).registrar(eq(null), eq("incidencia"), eq(100L),
                eq(AccionAuditoria.CREACION), eq(null), any());
    }

    // --- apoyo ------------------------------------------------------------------------------

    private Incidencia incidencia(EstadoIncidencia estado, Usuario afectado, Usuario creador) {
        Incidencia incidencia = Incidencia.builder().id(100L).usuario(afectado).creadoPor(creador)
                .tipo(TipoIncidencia.AUSENCIA).estado(estado).fechaEvento(AYER).descripcion("Descripción").build();
        // lenient: en algunas pruebas la regla falla antes de leer la incidencia, que es justo lo que se verifica.
        org.mockito.Mockito.lenient().when(incidenciaRepository.buscarParaActualizar(100L))
                .thenReturn(Optional.of(incidencia));
        return incidencia;
    }

    private RegistrarIncidenciaRequest solicitud(Long usuarioId, TipoIncidencia tipo, LocalDate fecha) {
        return new RegistrarIncidenciaRequest(usuarioId, null, tipo, fecha, "  Descripción del caso  ");
    }

    private Usuario usuario(Long id, String nombres, String rol) {
        return Usuario.builder().id(id).nombres(nombres).apellidos("Prueba")
                .rol(Rol.builder().id(id).nombre(rol).build()).build();
    }
}
