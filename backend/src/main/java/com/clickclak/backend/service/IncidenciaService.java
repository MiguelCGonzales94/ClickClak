package com.clickclak.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.HistorialIncidenciaResponse;
import com.clickclak.backend.dto.IncidenciaDetalleResponse;
import com.clickclak.backend.dto.IncidenciaResponse;
import com.clickclak.backend.dto.RegistrarIncidenciaRequest;
import com.clickclak.backend.exception.RecursoDuplicadoException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
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

/**
 * Gestión de incidencias laborales (el producto, no los incidentes de TI del servicio): alta,
 * consulta y flujo Registrada → En revisión → Aprobada/Rechazada → Cerrada. Cada cambio deja
 * una fila en el historial de la incidencia y otra en la bitácora de auditoría general.
 *
 * <p>Controles aplicados aquí y no en el controlador, para que valgan sin importar quién
 * invoque el servicio: el colaborador solo ve y registra lo suyo, solo el personal de revisión
 * cambia estados, y nadie revisa una incidencia que le afecta o que él mismo registró
 * (separación de funciones).
 */
@Service
public class IncidenciaService {

    private static final ZoneId ZONA_HORARIA_PERU = ZoneId.of("America/Lima");
    private static final String ENTIDAD_AUDITADA = "incidencia";

    /** Estados en los que una incidencia sigue "viva" y bloquea registrar otra igual. */
    private static final Set<EstadoIncidencia> ESTADOS_ABIERTOS =
            EnumSet.of(EstadoIncidencia.REGISTRADA, EstadoIncidencia.EN_REVISION, EstadoIncidencia.APROBADA);

    private static final Map<EstadoIncidencia, Set<EstadoIncidencia>> TRANSICIONES_PERMITIDAS = Map.of(
            EstadoIncidencia.REGISTRADA, EnumSet.of(EstadoIncidencia.EN_REVISION),
            EstadoIncidencia.EN_REVISION, EnumSet.of(EstadoIncidencia.APROBADA, EstadoIncidencia.RECHAZADA),
            EstadoIncidencia.APROBADA, EnumSet.of(EstadoIncidencia.CERRADA),
            EstadoIncidencia.RECHAZADA, EnumSet.of(EstadoIncidencia.CERRADA),
            EstadoIncidencia.CERRADA, EnumSet.noneOf(EstadoIncidencia.class));

    private final IncidenciaRepository incidenciaRepository;
    private final IncidenciaHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final MarcacionRepository marcacionRepository;
    private final AuditoriaService auditoriaService;

    public IncidenciaService(
            IncidenciaRepository incidenciaRepository,
            IncidenciaHistorialRepository historialRepository,
            UsuarioRepository usuarioRepository,
            MarcacionRepository marcacionRepository,
            AuditoriaService auditoriaService) {
        this.incidenciaRepository = incidenciaRepository;
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
        this.marcacionRepository = marcacionRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Un colaborador registra para sí mismo; el personal de revisión puede registrar a nombre
     * de un colaborador indicando {@code usuarioId}.
     */
    @Transactional
    public IncidenciaResponse registrar(Long actorId, RegistrarIncidenciaRequest solicitud) {
        Usuario actor = cargarUsuario(actorId);
        Usuario afectado = resolverAfectado(actor, solicitud.usuarioId());

        // Un permiso se puede pedir por adelantado; cualquier otro tipo describe algo ya ocurrido.
        if (solicitud.tipo() != TipoIncidencia.PERMISO
                && solicitud.fechaEvento().isAfter(LocalDate.now(ZONA_HORARIA_PERU))) {
            throw new SolicitudInvalidaException("La fecha del evento no puede ser futura");
        }

        Marcacion marcacion = null;
        if (solicitud.marcacionId() != null) {
            marcacion = marcacionRepository.findById(solicitud.marcacionId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Marcación no encontrada: " + solicitud.marcacionId()));
            if (!marcacion.getUsuario().getId().equals(afectado.getId())) {
                throw new SolicitudInvalidaException("La marcación indicada no pertenece al colaborador");
            }
        }

        if (incidenciaRepository.existsByUsuarioIdAndTipoAndFechaEventoAndEstadoIn(
                afectado.getId(), solicitud.tipo(), solicitud.fechaEvento(), ESTADOS_ABIERTOS)) {
            throw new RecursoDuplicadoException("Ya existe una incidencia " + solicitud.tipo()
                    + " abierta para ese colaborador en esa fecha");
        }

        Incidencia incidencia = persistirNueva(afectado, marcacion, solicitud.tipo(), solicitud.fechaEvento(),
                solicitud.descripcion().trim(), actor);
        auditoriaService.registrar(actor, ENTIDAD_AUDITADA, incidencia.getId(), AccionAuditoria.CREACION,
                null, instantanea(incidencia));
        return IncidenciaResponse.desde(incidencia);
    }

    /**
     * Alta hecha por el motor de validación contextual (p. ej. una tardanza detectada al marcar).
     * Pasa por el mismo camino que el alta manual para que toda incidencia nazca con su
     * historial y su auditoría; el autor del cambio en la bitácora es el sistema ({@code null}).
     */
    @Transactional
    public Incidencia registrarAutomatica(
            Usuario colaborador, Marcacion marcacion, TipoIncidencia tipo, LocalDate fechaEvento, String descripcion) {
        Incidencia incidencia = persistirNueva(colaborador, marcacion, tipo, fechaEvento, descripcion, colaborador);
        auditoriaService.registrar(null, ENTIDAD_AUDITADA, incidencia.getId(), AccionAuditoria.CREACION,
                null, instantanea(incidencia));
        return incidencia;
    }

    @Transactional(readOnly = true)
    public List<IncidenciaResponse> listarPropias(Long actorId, EstadoIncidencia estado) {
        return incidenciaRepository.buscar(actorId, estado).stream().map(IncidenciaResponse::desde).toList();
    }

    /** Vista de supervisión: todas las incidencias o las de un colaborador, opcionalmente por estado. */
    @Transactional(readOnly = true)
    public List<IncidenciaResponse> listar(Long usuarioId, EstadoIncidencia estado) {
        return incidenciaRepository.buscar(usuarioId, estado).stream().map(IncidenciaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public IncidenciaDetalleResponse obtener(Long actorId, Long incidenciaId) {
        Usuario actor = cargarUsuario(actorId);
        Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada: " + incidenciaId));

        if (!esPersonalDeRevision(actor) && !incidencia.getUsuario().getId().equals(actor.getId())) {
            throw new AccessDeniedException("No puede consultar la incidencia de otro colaborador");
        }

        List<HistorialIncidenciaResponse> traza = historialRepository.trazaDe(incidenciaId).stream()
                .map(HistorialIncidenciaResponse::desde).toList();
        return new IncidenciaDetalleResponse(IncidenciaResponse.desde(incidencia), traza);
    }

    @Transactional
    public IncidenciaResponse iniciarRevision(Long actorId, Long incidenciaId, String comentario) {
        return transicionar(actorId, incidenciaId, EstadoIncidencia.EN_REVISION, comentario);
    }

    @Transactional
    public IncidenciaResponse aprobar(Long actorId, Long incidenciaId, String comentario) {
        return transicionar(actorId, incidenciaId, EstadoIncidencia.APROBADA, comentario);
    }

    @Transactional
    public IncidenciaResponse rechazar(Long actorId, Long incidenciaId, String comentario) {
        if (comentario == null || comentario.isBlank()) {
            throw new SolicitudInvalidaException("Rechazar una incidencia exige indicar el motivo");
        }
        return transicionar(actorId, incidenciaId, EstadoIncidencia.RECHAZADA, comentario);
    }

    @Transactional
    public IncidenciaResponse cerrar(Long actorId, Long incidenciaId, String comentario) {
        return transicionar(actorId, incidenciaId, EstadoIncidencia.CERRADA, comentario);
    }

    private IncidenciaResponse transicionar(
            Long actorId, Long incidenciaId, EstadoIncidencia nuevoEstado, String comentario) {
        Usuario actor = cargarUsuario(actorId);
        if (!esPersonalDeRevision(actor)) {
            throw new AccessDeniedException("Solo el personal de revisión puede cambiar el estado de una incidencia");
        }

        Incidencia incidencia = incidenciaRepository.buscarParaActualizar(incidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada: " + incidenciaId));

        EstadoIncidencia estadoActual = incidencia.getEstado();
        if (!TRANSICIONES_PERMITIDAS.get(estadoActual).contains(nuevoEstado)) {
            throw new TransicionIncidenciaInvalidaException(estadoActual, nuevoEstado);
        }

        boolean esDecisionDeRevision = nuevoEstado != EstadoIncidencia.CERRADA;
        if (esDecisionDeRevision && (incidencia.getUsuario().getId().equals(actor.getId())
                || incidencia.getCreadoPor().getId().equals(actor.getId()))) {
            throw new AccessDeniedException("No puede revisar una incidencia que le afecta o que usted mismo registró");
        }

        Map<String, Object> antes = instantanea(incidencia);
        String comentarioLimpio = comentario == null || comentario.isBlank() ? null : comentario.trim();

        incidencia.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoIncidencia.APROBADA || nuevoEstado == EstadoIncidencia.RECHAZADA) {
            // Quién y cuándo resolvió van siempre juntos (restricción ck_incidencia_revision_completa).
            incidencia.setRevisadoPor(actor);
            incidencia.setRevisadoEn(Instant.now());
            incidencia.setComentarioRevision(comentarioLimpio);
        }
        incidenciaRepository.save(incidencia);

        historialRepository.save(IncidenciaHistorial.builder()
                .incidencia(incidencia)
                .estadoAnterior(estadoActual)
                .estadoNuevo(nuevoEstado)
                .usuario(actor)
                .comentario(comentarioLimpio)
                .build());
        auditoriaService.registrar(actor, ENTIDAD_AUDITADA, incidencia.getId(), AccionAuditoria.MODIFICACION,
                antes, instantanea(incidencia));

        return IncidenciaResponse.desde(incidencia);
    }

    private Incidencia persistirNueva(
            Usuario afectado, Marcacion marcacion, TipoIncidencia tipo, LocalDate fechaEvento,
            String descripcion, Usuario autor) {
        Incidencia incidencia = incidenciaRepository.save(Incidencia.builder()
                .usuario(afectado)
                .marcacion(marcacion)
                .tipo(tipo)
                .estado(EstadoIncidencia.REGISTRADA)
                .fechaEvento(fechaEvento)
                .descripcion(descripcion)
                .creadoPor(autor)
                .build());
        historialRepository.save(IncidenciaHistorial.builder()
                .incidencia(incidencia)
                .estadoAnterior(null)
                .estadoNuevo(EstadoIncidencia.REGISTRADA)
                .usuario(autor)
                .build());
        return incidencia;
    }

    private Usuario resolverAfectado(Usuario actor, Long usuarioIdSolicitado) {
        if (!esPersonalDeRevision(actor)) {
            if (usuarioIdSolicitado != null && !usuarioIdSolicitado.equals(actor.getId())) {
                throw new AccessDeniedException("No puede registrar una incidencia a nombre de otro colaborador");
            }
            return actor;
        }
        if (usuarioIdSolicitado == null) {
            throw new SolicitudInvalidaException("Debe indicar el colaborador al que corresponde la incidencia");
        }
        Usuario afectado = cargarUsuario(usuarioIdSolicitado);
        if (!afectado.isActivo()) {
            throw new SolicitudInvalidaException("El colaborador indicado está inactivo");
        }
        return afectado;
    }

    private Usuario cargarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));
    }

    private boolean esPersonalDeRevision(Usuario usuario) {
        String rol = usuario.getRol().getNombre();
        return Rol.SUPERVISOR.equals(rol) || Rol.RRHH_ADMIN.equals(rol);
    }

    /** Foto de los campos que importan para auditar; se serializa a JSON en la bitácora. */
    private Map<String, Object> instantanea(Incidencia incidencia) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("usuarioId", incidencia.getUsuario().getId());
        campos.put("tipo", incidencia.getTipo());
        campos.put("estado", incidencia.getEstado());
        campos.put("fechaEvento", incidencia.getFechaEvento());
        campos.put("revisadoPorId", incidencia.getRevisadoPor() == null ? null : incidencia.getRevisadoPor().getId());
        campos.put("comentarioRevision", incidencia.getComentarioRevision());
        return campos;
    }
}
