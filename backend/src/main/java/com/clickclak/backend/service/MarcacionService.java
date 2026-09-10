package com.clickclak.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.RegistrarMarcacionRequest;
import com.clickclak.backend.exception.DispositivoNoAutorizadoException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.model.Asignacion;
import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.EstadoIncidencia;
import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Incidencia;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.TipoEvento;
import com.clickclak.backend.model.TipoIncidencia;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.IncidenciaRepository;
import com.clickclak.backend.repository.MarcacionRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;

/**
 * Orquesta el registro de una marcación: idempotencia por {@code uuidCliente}, resolución de
 * la asignación vigente, validación contextual (espacial y de puntualidad) y generación
 * automática de incidencias por tardanza — el motor "clasifica y genera incidencias solo",
 * nunca mediante IA/ML, solo reglas.
 */
@Service
public class MarcacionService {

    /** Perú no tiene horario de verano: zona fija, sin necesidad de resolverla por usuario. */
    private static final ZoneId ZONA_HORARIA_PERU = ZoneId.of("America/Lima");

    private static final GeometryFactory FABRICA_GEOMETRIA = new GeometryFactory(new PrecisionModel(), 4326);

    private final MarcacionRepository marcacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final DispositivoRepository dispositivoRepository;
    private final AsignacionRepository asignacionRepository;
    private final UbicacionRepository ubicacionRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final MotorValidacionContextualService motorValidacionContextual;

    public MarcacionService(
            MarcacionRepository marcacionRepository,
            UsuarioRepository usuarioRepository,
            DispositivoRepository dispositivoRepository,
            AsignacionRepository asignacionRepository,
            UbicacionRepository ubicacionRepository,
            IncidenciaRepository incidenciaRepository,
            MotorValidacionContextualService motorValidacionContextual) {
        this.marcacionRepository = marcacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.asignacionRepository = asignacionRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.incidenciaRepository = incidenciaRepository;
        this.motorValidacionContextual = motorValidacionContextual;
    }

    @Transactional
    public Marcacion registrarMarcacion(RegistrarMarcacionRequest solicitud) {
        var existente = marcacionRepository.findByUuidCliente(solicitud.uuidCliente());
        if (existente.isPresent()) {
            // Reintento de sincronización offline (OE4): se devuelve la marcación ya
            // registrada en vez de duplicarla o fallar.
            return existente.get();
        }

        Usuario usuario = usuarioRepository.findById(solicitud.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + solicitud.usuarioId()));

        Dispositivo dispositivo = dispositivoRepository.findById(solicitud.dispositivoId())
                .filter(Dispositivo::isActivo)
                .orElseThrow(() -> new DispositivoNoAutorizadoException(solicitud.dispositivoId()));

        if (!dispositivo.getUsuario().getId().equals(usuario.getId())) {
            // El dispositivo existe y está activo, pero pertenece a otro colaborador: no
            // sirve como prueba de identidad para esta marcación.
            throw new DispositivoNoAutorizadoException(solicitud.dispositivoId());
        }

        LocalDate fechaEvento = solicitud.horaEvento().atZone(ZONA_HORARIA_PERU).toLocalDate();
        Asignacion asignacion = asignacionRepository.buscarVigente(usuario.getId(), fechaEvento).orElse(null);

        Point punto = FABRICA_GEOMETRIA.createPoint(new Coordinate(solicitud.longitud(), solicitud.latitud()));

        EstadoValidacion estadoValidacion;
        BigDecimal distanciaMetros = null;

        if (asignacion == null) {
            estadoValidacion = EstadoValidacion.SIN_ASIGNACION;
        } else {
            double distancia = ubicacionRepository.calcularDistanciaMetros(
                    asignacion.getUbicacion().getId(), solicitud.latitud(), solicitud.longitud());
            distanciaMetros = BigDecimal.valueOf(distancia).setScale(2, RoundingMode.HALF_UP);

            var resultado = motorValidacionContextual.validarUbicacion(
                    distancia, solicitud.precisionMetros(), asignacion.getUbicacion().getRadioToleranciaMetros());
            estadoValidacion = resultado.estado();
        }

        Marcacion marcacion = marcacionRepository.save(Marcacion.builder()
                .uuidCliente(solicitud.uuidCliente())
                .usuario(usuario)
                .asignacion(asignacion)
                .dispositivo(dispositivo)
                .tipoEvento(solicitud.tipoEvento())
                .horaEvento(solicitud.horaEvento())
                .geom(punto)
                .precisionMetros(solicitud.precisionMetros())
                .distanciaMetros(distanciaMetros)
                .estadoValidacion(estadoValidacion)
                .build());

        if (asignacion != null && solicitud.tipoEvento() == TipoEvento.ENTRADA) {
            var puntualidad = motorValidacionContextual.validarPuntualidad(
                    solicitud.tipoEvento(), solicitud.horaEvento(), asignacion.getHorario(), ZONA_HORARIA_PERU);
            if (puntualidad.tarde()) {
                generarIncidenciaTardanza(usuario, marcacion, puntualidad.minutosTarde(), fechaEvento);
            }
        }

        return marcacion;
    }

    private void generarIncidenciaTardanza(Usuario usuario, Marcacion marcacion, long minutosTarde, LocalDate fechaEvento) {
        incidenciaRepository.save(Incidencia.builder()
                .usuario(usuario)
                .marcacion(marcacion)
                .tipo(TipoIncidencia.TARDANZA)
                .estado(EstadoIncidencia.REGISTRADA)
                .fechaEvento(fechaEvento)
                .descripcion("Generada automáticamente por el motor de validación contextual: ingreso con "
                        + minutosTarde + " minuto(s) de tardanza respecto del horario asignado.")
                .creadoPor(usuario)
                .build());
    }
}
