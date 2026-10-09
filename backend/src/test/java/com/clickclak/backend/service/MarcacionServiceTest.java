package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.clickclak.backend.dto.RegistrarMarcacionRequest;
import com.clickclak.backend.exception.DispositivoNoAutorizadoException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.model.Asignacion;
import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.TipoEvento;
import com.clickclak.backend.model.TipoIncidencia;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.MarcacionRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class MarcacionServiceTest {

    @Mock private MarcacionRepository marcacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private DispositivoRepository dispositivoRepository;
    @Mock private AsignacionRepository asignacionRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private IncidenciaService incidenciaService;

    private MarcacionService marcacionService;

    private Usuario usuario;
    private Dispositivo dispositivo;

    @BeforeEach
    void configurar() {
        marcacionService = new MarcacionService(
                marcacionRepository, usuarioRepository, dispositivoRepository,
                asignacionRepository, ubicacionRepository, incidenciaService,
                new MotorValidacionContextualService());

        usuario = Usuario.builder().id(1L).nombres("Ana").apellidos("Pérez")
                .rol(Rol.builder().id(1L).nombre(Rol.COLABORADOR).build())
                .build();
        dispositivo = Dispositivo.builder().id(10L).usuario(usuario).activo(true).build();
    }

    @Test
    void marcacionYaRegistrada_devuelveLaExistenteSinDuplicar() {
        UUID uuidCliente = UUID.randomUUID();
        Marcacion existente = Marcacion.builder().id(99L).uuidCliente(uuidCliente).build();
        when(marcacionRepository.findByUuidCliente(uuidCliente)).thenReturn(Optional.of(existente));

        Marcacion resultado = marcacionService.registrarMarcacion(solicitud(uuidCliente));

        assertThat(resultado.getId()).isEqualTo(99L);
        verify(marcacionRepository, never()).save(any());
    }

    @Test
    void usuarioInexistente_lanzaExcepcion() {
        when(marcacionRepository.findByUuidCliente(any())).thenReturn(Optional.empty());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marcacionService.registrarMarcacion(solicitud(UUID.randomUUID())))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void dispositivoInactivo_lanzaExcepcion() {
        when(marcacionRepository.findByUuidCliente(any())).thenReturn(Optional.empty());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(dispositivoRepository.findById(10L)).thenReturn(Optional.of(
                Dispositivo.builder().id(10L).usuario(usuario).activo(false).build()));

        assertThatThrownBy(() -> marcacionService.registrarMarcacion(solicitud(UUID.randomUUID())))
                .isInstanceOf(DispositivoNoAutorizadoException.class);
    }

    @Test
    void dispositivoDeOtroUsuario_lanzaExcepcion() {
        Usuario otroUsuario = Usuario.builder().id(2L).nombres("Luis").apellidos("Gómez")
                .rol(Rol.builder().id(1L).nombre(Rol.COLABORADOR).build())
                .build();
        Dispositivo dispositivoDeOtro = Dispositivo.builder().id(10L).usuario(otroUsuario).activo(true).build();

        when(marcacionRepository.findByUuidCliente(any())).thenReturn(Optional.empty());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(dispositivoRepository.findById(10L)).thenReturn(Optional.of(dispositivoDeOtro));

        assertThatThrownBy(() -> marcacionService.registrarMarcacion(solicitud(UUID.randomUUID())))
                .isInstanceOf(DispositivoNoAutorizadoException.class);
    }

    @Test
    void sinAsignacionVigente_quedaMarcadaSinAsignacionYSinIncidencia() {
        when(marcacionRepository.findByUuidCliente(any())).thenReturn(Optional.empty());
        when(marcacionRepository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(dispositivoRepository.findById(10L)).thenReturn(Optional.of(dispositivo));
        when(asignacionRepository.buscarVigentes(anyLong(), any(LocalDate.class))).thenReturn(List.of());

        Marcacion resultado = marcacionService.registrarMarcacion(solicitud(UUID.randomUUID()));

        assertThat(resultado.getEstadoValidacion()).isEqualTo(EstadoValidacion.SIN_ASIGNACION);
        verify(incidenciaService, never()).registrarAutomatica(any(), any(), any(), any(), any());
    }

    @Test
    void entradaATiempoDentroDeTolerancia_quedaValidaYSinIncidencia() {
        Asignacion asignacion = asignacionDe(9, 0, 10, 150);
        when(marcacionRepository.findByUuidCliente(any())).thenReturn(Optional.empty());
        when(marcacionRepository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(dispositivoRepository.findById(10L)).thenReturn(Optional.of(dispositivo));
        when(asignacionRepository.buscarVigentes(anyLong(), any(LocalDate.class))).thenReturn(List.of(asignacion));
        when(ubicacionRepository.calcularDistanciaMetros(anyLong(), anyDouble(), anyDouble())).thenReturn(50.0);

        RegistrarMarcacionRequest solicitud = new RegistrarMarcacionRequest(
                UUID.randomUUID(), 1L, 10L, TipoEvento.ENTRADA, instanteLima(9, 5),
                -12.0464, -77.0428, BigDecimal.valueOf(10));

        Marcacion resultado = marcacionService.registrarMarcacion(solicitud);

        assertThat(resultado.getEstadoValidacion()).isEqualTo(EstadoValidacion.VALIDO);
        verify(incidenciaService, never()).registrarAutomatica(any(), any(), any(), any(), any());
    }

    @Test
    void entradaTardia_generaIncidenciaDeTardanzaAutomaticamente() {
        Asignacion asignacion = asignacionDe(9, 0, 10, 150);
        when(marcacionRepository.findByUuidCliente(any())).thenReturn(Optional.empty());
        when(marcacionRepository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(dispositivoRepository.findById(10L)).thenReturn(Optional.of(dispositivo));
        when(asignacionRepository.buscarVigentes(anyLong(), any(LocalDate.class))).thenReturn(List.of(asignacion));
        when(ubicacionRepository.calcularDistanciaMetros(anyLong(), anyDouble(), anyDouble())).thenReturn(50.0);

        RegistrarMarcacionRequest solicitud = new RegistrarMarcacionRequest(
                UUID.randomUUID(), 1L, 10L, TipoEvento.ENTRADA, instanteLima(9, 30),
                -12.0464, -77.0428, BigDecimal.valueOf(10));

        marcacionService.registrarMarcacion(solicitud);

        verify(incidenciaService).registrarAutomatica(any(), any(), eq(TipoIncidencia.TARDANZA), any(), any());
    }

    @Test
    void variasSedesVigentes_validaContraLaMasCercana() {
        Asignacion lejana = asignacionEn(1L, 1L, 9, 0, 10, 150);
        Asignacion cercana = asignacionEn(2L, 2L, 9, 0, 10, 150);
        prepararMarcacionConVigentes(List.of(lejana, cercana));
        when(ubicacionRepository.calcularDistanciaMetros(eq(1L), anyDouble(), anyDouble())).thenReturn(900.0);
        when(ubicacionRepository.calcularDistanciaMetros(eq(2L), anyDouble(), anyDouble())).thenReturn(40.0);

        Marcacion resultado = marcacionService.registrarMarcacion(solicitudEnHora(instanteLima(9, 5)));

        assertThat(resultado.getAsignacion().getId()).isEqualTo(2L);
        assertThat(resultado.getDistanciaMetros()).isEqualByComparingTo("40.00");
        assertThat(resultado.getEstadoValidacion()).isEqualTo(EstadoValidacion.VALIDO);
    }

    @Test
    void variasSedesVigentes_sinNingunaEnTolerancia_registraLaDistanciaDeLaMasProxima() {
        Asignacion primera = asignacionEn(1L, 1L, 9, 0, 10, 150);
        Asignacion segunda = asignacionEn(2L, 2L, 9, 0, 10, 150);
        prepararMarcacionConVigentes(List.of(primera, segunda));
        when(ubicacionRepository.calcularDistanciaMetros(eq(1L), anyDouble(), anyDouble())).thenReturn(5000.0);
        when(ubicacionRepository.calcularDistanciaMetros(eq(2L), anyDouble(), anyDouble())).thenReturn(1200.0);

        Marcacion resultado = marcacionService.registrarMarcacion(solicitudEnHora(instanteLima(9, 5)));

        assertThat(resultado.getAsignacion().getId()).isEqualTo(2L);
        assertThat(resultado.getDistanciaMetros()).isEqualByComparingTo("1200.00");
        assertThat(resultado.getEstadoValidacion()).isEqualTo(EstadoValidacion.FUERA_DE_TOLERANCIA);
    }

    @Test
    void variasSedesVigentes_conEmpateDeDistancia_ganaLaPrimeraDeLaLista() {
        Asignacion primera = asignacionEn(1L, 1L, 9, 0, 10, 150);
        Asignacion segunda = asignacionEn(2L, 2L, 9, 0, 10, 150);
        prepararMarcacionConVigentes(List.of(primera, segunda));
        when(ubicacionRepository.calcularDistanciaMetros(anyLong(), anyDouble(), anyDouble())).thenReturn(60.0);

        Marcacion resultado = marcacionService.registrarMarcacion(solicitudEnHora(instanteLima(9, 5)));

        assertThat(resultado.getAsignacion().getId()).isEqualTo(1L);
    }

    @Test
    void variasSedesVigentes_laTardanzaSeMideContraElTurnoDeLaSedeElegida() {
        Asignacion turnoTarde = asignacionEn(1L, 1L, 14, 0, 10, 150);
        Asignacion turnoManana = asignacionEn(2L, 2L, 9, 0, 10, 150);
        prepararMarcacionConVigentes(List.of(turnoTarde, turnoManana));
        // Marca a las 9:30 junto a la sede del turno de las 9:00: es tardanza de esa sede, aunque
        // frente al turno de las 14:00 de la otra sede no lo sería.
        when(ubicacionRepository.calcularDistanciaMetros(eq(1L), anyDouble(), anyDouble())).thenReturn(3000.0);
        when(ubicacionRepository.calcularDistanciaMetros(eq(2L), anyDouble(), anyDouble())).thenReturn(30.0);

        marcacionService.registrarMarcacion(solicitudEnHora(instanteLima(9, 30)));

        verify(incidenciaService).registrarAutomatica(any(), any(), eq(TipoIncidencia.TARDANZA), any(), any());
    }

    private void prepararMarcacionConVigentes(List<Asignacion> vigentes) {
        when(marcacionRepository.findByUuidCliente(any())).thenReturn(Optional.empty());
        when(marcacionRepository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(dispositivoRepository.findById(10L)).thenReturn(Optional.of(dispositivo));
        when(asignacionRepository.buscarVigentes(anyLong(), any(LocalDate.class))).thenReturn(vigentes);
    }

    private RegistrarMarcacionRequest solicitudEnHora(Instant horaEvento) {
        return new RegistrarMarcacionRequest(
                UUID.randomUUID(), 1L, 10L, TipoEvento.ENTRADA, horaEvento,
                -12.0464, -77.0428, BigDecimal.valueOf(10));
    }

    /** Una asignación en una sede propia (id de ubicación distinto), para probar varias a la vez. */
    private Asignacion asignacionEn(long asignacionId, long ubicacionId, int horaInicioH, int horaInicioM,
                                    int toleranciaMinutos, int radioToleranciaMetros) {
        Proyecto proyecto = Proyecto.builder().id(1L).nombre("Proyecto Demo").cliente("Cliente Demo").build();
        Ubicacion ubicacion = Ubicacion.builder().id(ubicacionId).proyecto(proyecto)
                .radioToleranciaMetros(radioToleranciaMetros).build();
        Horario horario = Horario.builder().id(asignacionId)
                .horaInicio(LocalTime.of(horaInicioH, horaInicioM))
                .horaFin(LocalTime.of(18, 0))
                .toleranciaMinutos(toleranciaMinutos)
                .build();
        return Asignacion.builder().id(asignacionId).usuario(usuario).proyecto(proyecto)
                .ubicacion(ubicacion).horario(horario).fechaInicio(LocalDate.of(2026, 1, 1)).build();
    }

    private RegistrarMarcacionRequest solicitud(UUID uuidCliente) {
        return new RegistrarMarcacionRequest(
                uuidCliente, 1L, 10L, TipoEvento.ENTRADA, instanteLima(9, 0),
                -12.0464, -77.0428, BigDecimal.valueOf(10));
    }

    private Asignacion asignacionDe(int horaInicioH, int horaInicioM, int toleranciaMinutos, int radioToleranciaMetros) {
        Proyecto proyecto = Proyecto.builder().id(1L).nombre("Proyecto Demo").cliente("Cliente Demo").build();
        Ubicacion ubicacion = Ubicacion.builder().id(1L).proyecto(proyecto)
                .radioToleranciaMetros(radioToleranciaMetros).build();
        Horario horario = Horario.builder().id(1L)
                .horaInicio(LocalTime.of(horaInicioH, horaInicioM))
                .horaFin(LocalTime.of(18, 0))
                .toleranciaMinutos(toleranciaMinutos)
                .build();
        return Asignacion.builder().id(1L).usuario(usuario).proyecto(proyecto)
                .ubicacion(ubicacion).horario(horario).fechaInicio(LocalDate.of(2026, 1, 1)).build();
    }

    private static Instant instanteLima(int hora, int minuto) {
        ZoneOffset offsetLima = ZoneOffset.ofHours(-5);
        return ZonedDateTime.of(2026, 9, 5, hora, minuto, 0, 0, offsetLima).toInstant();
    }
}
