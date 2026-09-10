package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.clickclak.backend.dto.RegistrarAsignacionRequest;
import com.clickclak.backend.exception.ConflictoAsignacionException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
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

    @Mock private AsignacionRepository asignacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private HorarioRepository horarioRepository;

    private AsignacionService asignacionService;

    private Usuario usuario;
    private Proyecto proyecto;
    private Ubicacion ubicacion;
    private Horario horario;

    @BeforeEach
    void configurar() {
        asignacionService = new AsignacionService(
                asignacionRepository, usuarioRepository, proyectoRepository, ubicacionRepository, horarioRepository);

        usuario = Usuario.builder().id(1L).nombres("Ana").apellidos("Pérez")
                .rol(Rol.builder().id(1L).nombre(Rol.COLABORADOR).build()).build();
        proyecto = Proyecto.builder().id(1L).nombre("Proyecto Demo").cliente("Cliente Demo").build();
        ubicacion = Ubicacion.builder().id(1L).proyecto(proyecto).nombre("Sede Demo").build();
        horario = Horario.builder().id(1L).nombre("Turno Demo").build();
    }

    @Test
    void fechaFinAnteriorAFechaInicio_lanzaSolicitudInvalida() {
        var solicitud = new RegistrarAsignacionRequest(
                1L, 1L, 1L, 1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> asignacionService.registrarAsignacion(solicitud))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void sinAsignacionesPrevias_registraSinConflicto() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(proyectoRepository.findById(1L)).thenReturn(Optional.of(proyecto));
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(ubicacion));
        when(horarioRepository.findById(1L)).thenReturn(Optional.of(horario));
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L)).thenReturn(List.of());
        when(asignacionRepository.save(any(Asignacion.class))).thenAnswer(inv -> {
            Asignacion a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        var solicitud = new RegistrarAsignacionRequest(
                1L, 1L, 1L, 1L, LocalDate.of(2026, 3, 1), null);

        var resultado = asignacionService.registrarAsignacion(solicitud);

        assertThat(resultado.id()).isEqualTo(99L);
        assertThat(resultado.nombreUsuario()).isEqualTo("Ana Pérez");
    }

    @Test
    void rangoSuperpuestoConAsignacionActiva_lanzaConflicto() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(proyectoRepository.findById(1L)).thenReturn(Optional.of(proyecto));
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(ubicacion));
        when(horarioRepository.findById(1L)).thenReturn(Optional.of(horario));

        Asignacion existente = Asignacion.builder().id(50L).usuario(usuario)
                .fechaInicio(LocalDate.of(2026, 3, 1)).fechaFin(LocalDate.of(2026, 3, 31)).build();
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L))
                .thenReturn(List.of(existente));

        var solicitud = new RegistrarAsignacionRequest(
                1L, 1L, 1L, 1L, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15));

        assertThatThrownBy(() -> asignacionService.registrarAsignacion(solicitud))
                .isInstanceOf(ConflictoAsignacionException.class);
    }

    @Test
    void rangoConsecutivoSinSolapar_noGeneraConflicto() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(proyectoRepository.findById(1L)).thenReturn(Optional.of(proyecto));
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(ubicacion));
        when(horarioRepository.findById(1L)).thenReturn(Optional.of(horario));

        Asignacion existente = Asignacion.builder().id(50L).usuario(usuario)
                .fechaInicio(LocalDate.of(2026, 3, 1)).fechaFin(LocalDate.of(2026, 3, 31)).build();
        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L))
                .thenReturn(List.of(existente));
        when(asignacionRepository.save(any(Asignacion.class))).thenAnswer(inv -> inv.getArgument(0));

        var solicitud = new RegistrarAsignacionRequest(
                1L, 1L, 1L, 1L, LocalDate.of(2026, 4, 1), null);

        var resultado = asignacionService.registrarAsignacion(solicitud);

        assertThat(resultado.fechaInicio()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void agenda_excluyeAsignacionesYaVencidas() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        Asignacion vencida = Asignacion.builder().id(1L).usuario(usuario).proyecto(proyecto)
                .ubicacion(ubicacion).horario(horario)
                .fechaInicio(hoy.minusDays(30)).fechaFin(hoy.minusDays(1)).build();
        Asignacion vigente = Asignacion.builder().id(2L).usuario(usuario).proyecto(proyecto)
                .ubicacion(ubicacion).horario(horario)
                .fechaInicio(hoy.minusDays(5)).fechaFin(null).build();

        when(asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(1L))
                .thenReturn(List.of(vencida, vigente));

        var agenda = asignacionService.obtenerAgenda(1L);

        assertThat(agenda).hasSize(1);
        assertThat(agenda.get(0).id()).isEqualTo(2L);
        assertThat(agenda.get(0).estado()).isEqualTo("VIGENTE");
    }
}
