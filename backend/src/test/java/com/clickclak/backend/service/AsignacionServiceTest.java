package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.clickclak.backend.dto.EditarAsignacionRequest;
import com.clickclak.backend.dto.MoverAsignacionRequest;
import com.clickclak.backend.dto.RegistrarAsignacionRequest;
import com.clickclak.backend.exception.ConflictoAsignacionException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.Asignacion;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.HorarioRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AsignacionServiceTest {

    private static final Long ACTOR = 5L;

    @Mock private AsignacionRepository asignacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private AuditoriaService auditoriaService;

    private AsignacionService asignacionService;

    private Usuario usuario;
    private Proyecto proyecto;
    private Proyecto otroProyecto;
    private Ubicacion sedeA;
    private Ubicacion sedeB;
    private Ubicacion sedeDeOtroProyecto;
    private Horario horario;
    private Horario otroHorario;

    @BeforeEach
    void configurar() {
        asignacionService = new AsignacionService(
                asignacionRepository, usuarioRepository, proyectoRepository, ubicacionRepository, horarioRepository,
                auditoriaService);

        usuario = Usuario.builder().id(1L).nombres("Ana").apellidos("Pérez")
                .rol(Rol.builder().id(1L).nombre(Rol.COLABORADOR).build()).build();
        proyecto = Proyecto.builder().id(1L).nombre("Proyecto Demo").cliente("Cliente Demo").build();
        otroProyecto = Proyecto.builder().id(2L).nombre("Otro Proyecto").cliente("Cliente Demo").build();
        sedeA = Ubicacion.builder().id(1L).proyecto(proyecto).nombre("Sede A").build();
        sedeB = Ubicacion.builder().id(2L).proyecto(proyecto).nombre("Sede B").build();
        sedeDeOtroProyecto = Ubicacion.builder().id(3L).proyecto(otroProyecto).nombre("Sede Ajena").build();
        horario = Horario.builder().id(1L).nombre("Turno Demo").build();
        otroHorario = Horario.builder().id(2L).nombre("Turno Tarde").build();

        lenient().when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        lenient().when(usuarioRepository.getReferenceById(ACTOR)).thenReturn(Usuario.builder().id(ACTOR).build());
        lenient().when(proyectoRepository.findById(1L)).thenReturn(Optional.of(proyecto));
        lenient().when(proyectoRepository.findById(2L)).thenReturn(Optional.of(otroProyecto));
        lenient().when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(sedeA));
        lenient().when(ubicacionRepository.findById(2L)).thenReturn(Optional.of(sedeB));
        lenient().when(ubicacionRepository.findById(3L)).thenReturn(Optional.of(sedeDeOtroProyecto));
        lenient().when(horarioRepository.findById(1L)).thenReturn(Optional.of(horario));
        lenient().when(horarioRepository.findById(2L)).thenReturn(Optional.of(otroHorario));
        lenient().when(asignacionRepository.save(any(Asignacion.class))).thenAnswer(inv -> {
            Asignacion a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(99L);
            }
            return a;
        });
    }

    // ---- registrar

    @Test
    void fechaFinAnteriorAFechaInicio_lanzaSolicitudInvalida() {
        var solicitud = new RegistrarAsignacionRequest(
                1L, 1L, 1L, 1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> asignacionService.registrarAsignacion(solicitud, ACTOR))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void sinAsignacionesPrevias_registraYDejaConstanciaEnLaBitacora() {
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of());

        var resultado = asignacionService.registrarAsignacion(
                new RegistrarAsignacionRequest(1L, 1L, 1L, 1L, LocalDate.of(2026, 3, 1), null), ACTOR);

        assertThat(resultado.id()).isEqualTo(99L);
        assertThat(resultado.nombreUsuario()).isEqualTo("Ana Pérez");
        verify(auditoriaService).registrar(any(), eq("asignacion"), eq(99L), eq(AccionAuditoria.CREACION), eq(null), any());
    }

    @Test
    void mismaSedeConRangoSuperpuesto_lanzaConflicto() {
        Asignacion existente = asignacion(50L, sedeA, horario, "2026-03-01", "2026-03-31");
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(existente));

        var solicitud = new RegistrarAsignacionRequest(
                1L, 1L, 1L, 1L, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15));

        assertThatThrownBy(() -> asignacionService.registrarAsignacion(solicitud, ACTOR))
                .isInstanceOf(ConflictoAsignacionException.class)
                .hasMessageContaining("Sede A");
        verify(asignacionRepository, never()).save(any());
    }

    @Test
    void otraSedeConElMismoRango_sePermiteTenerVariasSedesALaVez() {
        Asignacion enSedeA = asignacion(50L, sedeA, horario, "2026-03-01", "2026-03-31");
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(enSedeA));

        var resultado = asignacionService.registrarAsignacion(
                new RegistrarAsignacionRequest(1L, 1L, 2L, 1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), ACTOR);

        assertThat(resultado.nombreUbicacion()).isEqualTo("Sede B");
    }

    @Test
    void mismaSedeEnRangoConsecutivo_noGeneraConflicto() {
        Asignacion existente = asignacion(50L, sedeA, horario, "2026-03-01", "2026-03-31");
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(existente));

        var resultado = asignacionService.registrarAsignacion(
                new RegistrarAsignacionRequest(1L, 1L, 1L, 1L, LocalDate.of(2026, 4, 1), null), ACTOR);

        assertThat(resultado.fechaInicio()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void sedeDeOtroServicio_lanzaSolicitudInvalida() {
        var solicitud = new RegistrarAsignacionRequest(1L, 1L, 3L, 1L, LocalDate.of(2026, 3, 1), null);

        assertThatThrownBy(() -> asignacionService.registrarAsignacion(solicitud, ACTOR))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("no pertenece al servicio");
    }

    // ---- editar

    @Test
    void editar_cambiaSedeTurnoYFechasYAudita() {
        Asignacion existente = asignacion(50L, sedeA, horario, "2026-03-01", null);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(existente));
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(existente));

        var resultado = asignacionService.editarAsignacion(50L,
                new EditarAsignacionRequest(1L, 2L, 2L, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 6, 30)), ACTOR);

        assertThat(resultado.nombreUbicacion()).isEqualTo("Sede B");
        assertThat(resultado.nombreHorario()).isEqualTo("Turno Tarde");
        assertThat(existente.getFechaFin()).isEqualTo(LocalDate.of(2026, 6, 30));
        verify(auditoriaService).registrar(any(), eq("asignacion"), eq(50L), eq(AccionAuditoria.MODIFICACION), any(), any());
    }

    @Test
    void editar_noChocaConsigoMisma() {
        Asignacion existente = asignacion(50L, sedeA, horario, "2026-03-01", "2026-03-31");
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(existente));
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(existente));

        var resultado = asignacionService.editarAsignacion(50L,
                new EditarAsignacionRequest(1L, 1L, 2L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 20)), ACTOR);

        assertThat(resultado.fechaInicio()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void editar_aLaSedeQueElTecnicoYaTieneEnEsasFechas_lanzaConflicto() {
        Asignacion enSedeA = asignacion(50L, sedeA, horario, "2026-03-01", "2026-03-31");
        Asignacion enSedeB = asignacion(51L, sedeB, horario, "2026-03-01", "2026-03-31");
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(enSedeA));
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L))
                .thenReturn(List.of(enSedeA, enSedeB));

        var solicitud = new EditarAsignacionRequest(1L, 2L, 1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThatThrownBy(() -> asignacionService.editarAsignacion(50L, solicitud, ACTOR))
                .isInstanceOf(ConflictoAsignacionException.class);
        assertThat(enSedeA.getUbicacion().getId()).isEqualTo(1L);
    }

    @Test
    void editar_unaAsignacionInexistenteOQuitada_lanzaNoEncontrado() {
        Asignacion quitada = asignacion(50L, sedeA, horario, "2026-03-01", null);
        quitada.setActivo(false);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(quitada));
        when(asignacionRepository.findById(404L)).thenReturn(Optional.empty());
        var solicitud = new EditarAsignacionRequest(1L, 1L, 1L, LocalDate.of(2026, 3, 1), null);

        assertThatThrownBy(() -> asignacionService.editarAsignacion(50L, solicitud, ACTOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> asignacionService.editarAsignacion(404L, solicitud, ACTOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- quitar

    @Test
    void quitar_haceBajaLogicaYAudita() {
        Asignacion existente = asignacion(50L, sedeA, horario, "2026-03-01", null);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(existente));

        asignacionService.quitarAsignacion(50L, ACTOR);

        assertThat(existente.isActivo()).isFalse();
        verify(asignacionRepository, never()).delete(any());
        verify(auditoriaService).registrar(any(), eq("asignacion"), eq(50L), eq(AccionAuditoria.MODIFICACION), any(), any());
    }

    @Test
    void quitar_unaYaQuitada_lanzaNoEncontrado() {
        Asignacion quitada = asignacion(50L, sedeA, horario, "2026-03-01", null);
        quitada.setActivo(false);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(quitada));

        assertThatThrownBy(() -> asignacionService.quitarAsignacion(50L, ACTOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- mover

    @Test
    void mover_terminaLaActualElDiaAnteriorYCreaLaNuevaHeredandoElFin() {
        Asignacion actual = asignacion(50L, sedeA, horario, "2026-03-01", "2026-12-31");
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(actual));
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(actual));

        var nueva = asignacionService.moverAsignacion(50L,
                new MoverAsignacionRequest(2L, null, null, LocalDate.of(2026, 6, 1)), ACTOR);

        assertThat(actual.getFechaFin()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(nueva.id()).isEqualTo(99L);
        assertThat(nueva.nombreUbicacion()).isEqualTo("Sede B");
        assertThat(nueva.nombreHorario()).isEqualTo("Turno Demo");
        assertThat(nueva.fechaInicio()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(nueva.fechaFin()).isEqualTo(LocalDate.of(2026, 12, 31));
        verify(auditoriaService, times(2)).registrar(any(), eq("asignacion"), any(), any(), any(), any());
    }

    @Test
    void mover_puedeCambiarElTurnoAlMismoTiempo() {
        Asignacion actual = asignacion(50L, sedeA, horario, "2026-03-01", null);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(actual));
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(actual));

        var nueva = asignacionService.moverAsignacion(50L,
                new MoverAsignacionRequest(2L, null, 2L, LocalDate.of(2026, 6, 1)), ACTOR);

        assertThat(nueva.nombreHorario()).isEqualTo("Turno Tarde");
        assertThat(nueva.fechaFin()).isNull();
    }

    @Test
    void mover_conFechaDeCambioAnteriorOIgualAlInicio_lanzaSolicitudInvalida() {
        Asignacion actual = asignacion(50L, sedeA, horario, "2026-03-01", null);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(actual));
        var solicitud = new MoverAsignacionRequest(2L, null, null, LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> asignacionService.moverAsignacion(50L, solicitud, ACTOR))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("posterior al inicio");
        assertThat(actual.getFechaFin()).isNull();
    }

    @Test
    void mover_unaAsignacionQueYaTerminoAntesDelCambio_lanzaSolicitudInvalida() {
        Asignacion actual = asignacion(50L, sedeA, horario, "2026-03-01", "2026-04-30");
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(actual));
        var solicitud = new MoverAsignacionRequest(2L, null, null, LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> asignacionService.moverAsignacion(50L, solicitud, ACTOR))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("termina el 2026-04-30");
    }

    @Test
    void mover_ALaMismaSede_lanzaSolicitudInvalida() {
        Asignacion actual = asignacion(50L, sedeA, horario, "2026-03-01", null);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(actual));
        var solicitud = new MoverAsignacionRequest(1L, null, null, LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> asignacionService.moverAsignacion(50L, solicitud, ACTOR))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("ya está en esa sede");
    }

    @Test
    void mover_aUnaSedeQueYaTieneEnEsasFechas_lanzaConflictoYNoTocaLaActual() {
        Asignacion actual = asignacion(50L, sedeA, horario, "2026-03-01", null);
        Asignacion yaEnB = asignacion(51L, sedeB, horario, "2026-05-01", null);
        when(asignacionRepository.findById(50L)).thenReturn(Optional.of(actual));
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of(actual, yaEnB));
        var solicitud = new MoverAsignacionRequest(2L, null, null, LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> asignacionService.moverAsignacion(50L, solicitud, ACTOR))
                .isInstanceOf(ConflictoAsignacionException.class);
        assertThat(actual.getFechaFin()).isNull();
        verify(asignacionRepository, never()).save(any());
    }

    // ---- agenda y lista

    @Test
    void agenda_excluyeAsignacionesYaVencidasYMuestraLasVariasSedesVigentes() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        Asignacion vencida = asignacion(1L, sedeA, horario, hoy.minusDays(30).toString(), hoy.minusDays(1).toString());
        Asignacion enA = asignacion(2L, sedeA, horario, hoy.minusDays(5).toString(), null);
        Asignacion enB = asignacion(3L, sedeB, horario, hoy.minusDays(2).toString(), null);

        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L))
                .thenReturn(List.of(vencida, enA, enB));

        var agenda = asignacionService.obtenerAgenda(1L);

        assertThat(agenda).extracting(a -> a.id()).containsExactly(2L, 3L);
        assertThat(agenda).extracting(a -> a.estado()).containsOnly("VIGENTE");
    }

    @Test
    void lista_distingueVigenteProgramadaYFinalizada() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        Asignacion finalizada = asignacion(1L, sedeA, horario, hoy.minusDays(30).toString(), hoy.minusDays(1).toString());
        Asignacion vigente = asignacion(2L, sedeA, horario, hoy.minusDays(5).toString(), null);
        Asignacion terminaHoy = asignacion(3L, sedeB, horario, hoy.minusDays(5).toString(), hoy.toString());
        Asignacion programada = asignacion(4L, sedeB, horario, hoy.plusDays(3).toString(), null);
        when(asignacionRepository.findByActivoTrueOrderByFechaInicioDesc())
                .thenReturn(List.of(finalizada, vigente, terminaHoy, programada));

        var lista = asignacionService.listarAsignaciones(null);

        assertThat(lista).extracting(a -> a.estado())
                .containsExactly("FINALIZADA", "VIGENTE", "VIGENTE", "PROGRAMADA");
    }

    private Asignacion asignacion(Long id, Ubicacion sede, Horario turno, String inicio, String fin) {
        return Asignacion.builder().id(id).usuario(usuario).proyecto(proyecto)
                .ubicacion(sede).horario(turno)
                .fechaInicio(LocalDate.parse(inicio)).fechaFin(fin != null ? LocalDate.parse(fin) : null)
                .build();
    }
}
