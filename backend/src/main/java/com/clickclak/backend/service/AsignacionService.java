package com.clickclak.backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.AsignacionResponse;
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
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.HorarioRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;

/**
 * HU08/HU09: asignación de un colaborador a un proyecto, sede y horario en un rango de fechas;
 * edición, baja y traslado a otra sede; y consulta de la agenda propia. Las respuestas se
 * ensamblan aquí mismo (no en el controlador) porque incluyen nombres de entidades relacionadas
 * cargadas de forma perezosa — fuera de esta transacción fallarían.
 *
 * <p><b>Un técnico puede tener varias sedes a la vez.</b> Lo único que no puede repetirse es la
 * misma sede en fechas que se cruzan (sería ambiguo cuál fila manda). Al marcar, el servidor valida
 * contra la más cercana de las sedes vigentes (ver {@link MarcacionService}). Cada cambio queda en
 * la bitácora de auditoría. Quitar es una baja lógica: las marcaciones ya hechas conservan su
 * asignación.
 */
@Service
public class AsignacionService {

    private static final ZoneId ZONA_HORARIA_PERU = ZoneId.of("America/Lima");

    private final AsignacionRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProyectoRepository proyectoRepository;
    private final UbicacionRepository ubicacionRepository;
    private final HorarioRepository horarioRepository;
    private final AuditoriaService auditoriaService;

    public AsignacionService(
            AsignacionRepository asignacionRepository,
            UsuarioRepository usuarioRepository,
            ProyectoRepository proyectoRepository,
            UbicacionRepository ubicacionRepository,
            HorarioRepository horarioRepository,
            AuditoriaService auditoriaService) {
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.proyectoRepository = proyectoRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.horarioRepository = horarioRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public AsignacionResponse registrarAsignacion(RegistrarAsignacionRequest solicitud, Long actorId) {
        validarFechas(solicitud.fechaInicio(), solicitud.fechaFin());

        Usuario usuario = cargarUsuario(solicitud.usuarioId());
        Proyecto proyecto = cargarProyecto(solicitud.proyectoId());
        Ubicacion ubicacion = cargarUbicacion(solicitud.ubicacionId());
        Horario horario = cargarHorario(solicitud.horarioId());

        validarSedeDelProyecto(ubicacion, proyecto);
        validarSinConflicto(usuario, ubicacion, solicitud.fechaInicio(), solicitud.fechaFin(), null);

        Asignacion asignacion = asignacionRepository.save(Asignacion.builder()
                .usuario(usuario).proyecto(proyecto).ubicacion(ubicacion).horario(horario)
                .fechaInicio(solicitud.fechaInicio()).fechaFin(solicitud.fechaFin())
                .build());

        auditar(actorId, asignacion, AccionAuditoria.CREACION, null, capturarEstado(asignacion));
        return AsignacionResponse.desde(asignacion, hoy());
    }

    /** Cambia servicio, sede, turno o fechas. El técnico no cambia: para eso se crea otra asignación. */
    @Transactional
    public AsignacionResponse editarAsignacion(Long asignacionId, EditarAsignacionRequest solicitud, Long actorId) {
        Asignacion asignacion = obtenerActiva(asignacionId);
        validarFechas(solicitud.fechaInicio(), solicitud.fechaFin());

        Proyecto proyecto = cargarProyecto(solicitud.proyectoId());
        Ubicacion ubicacion = cargarUbicacion(solicitud.ubicacionId());
        Horario horario = cargarHorario(solicitud.horarioId());

        validarSedeDelProyecto(ubicacion, proyecto);
        validarSinConflicto(asignacion.getUsuario(), ubicacion, solicitud.fechaInicio(), solicitud.fechaFin(),
                asignacion.getId());

        Map<String, Object> anterior = capturarEstado(asignacion);
        asignacion.setProyecto(proyecto);
        asignacion.setUbicacion(ubicacion);
        asignacion.setHorario(horario);
        asignacion.setFechaInicio(solicitud.fechaInicio());
        asignacion.setFechaFin(solicitud.fechaFin());
        asignacionRepository.save(asignacion);

        auditar(actorId, asignacion, AccionAuditoria.MODIFICACION, anterior, capturarEstado(asignacion));
        return AsignacionResponse.desde(asignacion, hoy());
    }

    /**
     * Baja lógica: la asignación deja de contar para validar marcaciones y desaparece de la agenda y de
     * la lista, pero las marcaciones que ya la usaron siguen apuntando a ella.
     */
    @Transactional
    public void quitarAsignacion(Long asignacionId, Long actorId) {
        Asignacion asignacion = obtenerActiva(asignacionId);
        Map<String, Object> anterior = capturarEstado(asignacion);

        asignacion.setActivo(false);
        asignacionRepository.save(asignacion);

        auditar(actorId, asignacion, AccionAuditoria.MODIFICACION, anterior, capturarEstado(asignacion));
    }

    /**
     * Quitar de una sede y poner en otra, en una sola transacción: si el segundo paso falla, el técnico
     * no queda sin asignación. La actual termina el día anterior a {@code fechaCambio} y la nueva
     * hereda la fecha de fin de la que se deja. Devuelve la asignación nueva.
     */
    @Transactional
    public AsignacionResponse moverAsignacion(Long asignacionId, MoverAsignacionRequest solicitud, Long actorId) {
        Asignacion actual = obtenerActiva(asignacionId);
        LocalDate fechaCambio = solicitud.fechaCambio();

        if (!fechaCambio.isAfter(actual.getFechaInicio())) {
            throw new SolicitudInvalidaException(
                    "La fecha de cambio debe ser posterior al inicio de la asignación actual ("
                            + actual.getFechaInicio() + "); si todavía no empezó, edítela o quítela");
        }
        if (actual.getFechaFin() != null && actual.getFechaFin().isBefore(fechaCambio)) {
            throw new SolicitudInvalidaException(
                    "La asignación actual termina el " + actual.getFechaFin() + ", antes de la fecha de cambio");
        }
        if (solicitud.ubicacionId().equals(actual.getUbicacion().getId())) {
            throw new SolicitudInvalidaException("El técnico ya está en esa sede: para cambiar el turno o las fechas, edite la asignación");
        }

        Proyecto proyecto = solicitud.proyectoId() != null ? cargarProyecto(solicitud.proyectoId()) : actual.getProyecto();
        Ubicacion ubicacion = cargarUbicacion(solicitud.ubicacionId());
        Horario horario = solicitud.horarioId() != null ? cargarHorario(solicitud.horarioId()) : actual.getHorario();
        validarSedeDelProyecto(ubicacion, proyecto);
        // La actual ya no cuenta para el cruce de fechas de la sede nueva: se está dejando.
        validarSinConflicto(actual.getUsuario(), ubicacion, fechaCambio, actual.getFechaFin(), actual.getId());

        Map<String, Object> anterior = capturarEstado(actual);
        LocalDate finOriginal = actual.getFechaFin();
        actual.setFechaFin(fechaCambio.minusDays(1));
        asignacionRepository.save(actual);
        auditar(actorId, actual, AccionAuditoria.MODIFICACION, anterior, capturarEstado(actual));

        Asignacion nueva = asignacionRepository.save(Asignacion.builder()
                .usuario(actual.getUsuario()).proyecto(proyecto).ubicacion(ubicacion).horario(horario)
                .fechaInicio(fechaCambio).fechaFin(finOriginal)
                .build());
        auditar(actorId, nueva, AccionAuditoria.CREACION, null, capturarEstado(nueva));

        return AsignacionResponse.desde(nueva, hoy());
    }

    /** HU09: asignaciones vigentes o futuras del colaborador autenticado, en orden de inicio. */
    @Transactional(readOnly = true)
    public List<AsignacionResponse> obtenerAgenda(Long usuarioId) {
        LocalDate hoy = hoy();
        return asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(usuarioId).stream()
                .filter(asignacion -> asignacion.getFechaFin() == null || !asignacion.getFechaFin().isBefore(hoy))
                .map(asignacion -> AsignacionResponse.desde(asignacion, hoy))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AsignacionResponse> listarAsignaciones(Long usuarioIdFiltro) {
        LocalDate hoy = hoy();
        List<Asignacion> asignaciones = usuarioIdFiltro != null
                ? asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(usuarioIdFiltro)
                : asignacionRepository.findByActivoTrueOrderByFechaInicioDesc();
        return asignaciones.stream().map(asignacion -> AsignacionResponse.desde(asignacion, hoy)).toList();
    }

    private LocalDate hoy() {
        return LocalDate.now(ZONA_HORARIA_PERU);
    }

    private void validarFechas(LocalDate inicio, LocalDate fin) {
        if (fin != null && fin.isBefore(inicio)) {
            throw new SolicitudInvalidaException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
    }

    /** Una sede pertenece a un servicio: asignar la sede de otro servicio dejaría datos incoherentes. */
    private void validarSedeDelProyecto(Ubicacion ubicacion, Proyecto proyecto) {
        if (ubicacion.getProyecto() == null || !ubicacion.getProyecto().getId().equals(proyecto.getId())) {
            throw new SolicitudInvalidaException(
                    "La sede \"" + ubicacion.getNombre() + "\" no pertenece al servicio \"" + proyecto.getNombre() + "\"");
        }
    }

    /** Solo choca la misma sede con fechas cruzadas; otras sedes del mismo técnico no importan. */
    private void validarSinConflicto(Usuario usuario, Ubicacion ubicacion, LocalDate inicio, LocalDate fin, Long excluirId) {
        boolean hayConflicto = asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(usuario.getId()).stream()
                .filter(existente -> !existente.getId().equals(excluirId))
                .filter(existente -> existente.getUbicacion() != null
                        && existente.getUbicacion().getId().equals(ubicacion.getId()))
                .anyMatch(existente -> seSuperponen(inicio, fin, existente.getFechaInicio(), existente.getFechaFin()));
        if (hayConflicto) {
            throw new ConflictoAsignacionException(
                    (usuario.getNombres() + " " + usuario.getApellidos()).trim(), ubicacion.getNombre());
        }
    }

    /** Dos rangos [inicioA,finA] y [inicioB,finB] (fin nulo = sin fecha de término) se cruzan. */
    private boolean seSuperponen(LocalDate inicioA, LocalDate finA, LocalDate inicioB, LocalDate finB) {
        boolean aEmpiezaAntesDeQueTermineB = finB == null || !inicioA.isAfter(finB);
        boolean bEmpiezaAntesDeQueTermineA = finA == null || !inicioB.isAfter(finA);
        return aEmpiezaAntesDeQueTermineB && bEmpiezaAntesDeQueTermineA;
    }

    private Asignacion obtenerActiva(Long asignacionId) {
        return asignacionRepository.findById(asignacionId)
                .filter(Asignacion::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Asignación no encontrada: " + asignacionId));
    }

    private Usuario cargarUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));
    }

    private Proyecto cargarProyecto(Long id) {
        return proyectoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + id));
    }

    private Ubicacion cargarUbicacion(Long id) {
        return ubicacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ubicación no encontrada: " + id));
    }

    private Horario cargarHorario(Long id) {
        return horarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Horario no encontrado: " + id));
    }

    private Map<String, Object> capturarEstado(Asignacion asignacion) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("usuarioId", asignacion.getUsuario().getId());
        estado.put("proyectoId", asignacion.getProyecto().getId());
        estado.put("ubicacionId", asignacion.getUbicacion().getId());
        estado.put("horarioId", asignacion.getHorario().getId());
        estado.put("fechaInicio", asignacion.getFechaInicio().toString());
        estado.put("fechaFin", asignacion.getFechaFin() != null ? asignacion.getFechaFin().toString() : null);
        estado.put("activo", asignacion.isActivo());
        return estado;
    }

    private void auditar(Long actorId, Asignacion asignacion, AccionAuditoria accion,
                         Map<String, Object> anterior, Map<String, Object> nuevo) {
        Usuario actor = actorId != null ? usuarioRepository.getReferenceById(actorId) : null;
        auditoriaService.registrar(actor, "asignacion", asignacion.getId(), accion, anterior, nuevo);
    }
}
