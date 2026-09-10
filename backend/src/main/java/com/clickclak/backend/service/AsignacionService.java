package com.clickclak.backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.AsignacionResponse;
import com.clickclak.backend.dto.RegistrarAsignacionRequest;
import com.clickclak.backend.exception.ConflictoAsignacionException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
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
 * HU08/HU09: asignación de un colaborador a un proyecto, sede y horario en un rango de
 * fechas, con detección de conflictos, y consulta de la agenda propia. Las respuestas se
 * ensamblan aquí mismo (no en el controlador) porque incluyen nombres de entidades
 * relacionadas cargadas de forma perezosa — fuera de esta transacción fallarían.
 */
@Service
public class AsignacionService {

    private static final ZoneId ZONA_HORARIA_PERU = ZoneId.of("America/Lima");

    private final AsignacionRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProyectoRepository proyectoRepository;
    private final UbicacionRepository ubicacionRepository;
    private final HorarioRepository horarioRepository;

    public AsignacionService(
            AsignacionRepository asignacionRepository,
            UsuarioRepository usuarioRepository,
            ProyectoRepository proyectoRepository,
            UbicacionRepository ubicacionRepository,
            HorarioRepository horarioRepository) {
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.proyectoRepository = proyectoRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.horarioRepository = horarioRepository;
    }

    @Transactional
    public AsignacionResponse registrarAsignacion(RegistrarAsignacionRequest solicitud) {
        if (solicitud.fechaFin() != null && solicitud.fechaFin().isBefore(solicitud.fechaInicio())) {
            throw new SolicitudInvalidaException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        Usuario usuario = usuarioRepository.findById(solicitud.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + solicitud.usuarioId()));
        Proyecto proyecto = proyectoRepository.findById(solicitud.proyectoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + solicitud.proyectoId()));
        Ubicacion ubicacion = ubicacionRepository.findById(solicitud.ubicacionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Ubicación no encontrada: " + solicitud.ubicacionId()));
        Horario horario = horarioRepository.findById(solicitud.horarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Horario no encontrado: " + solicitud.horarioId()));

        boolean hayConflicto = asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(usuario.getId())
                .stream()
                .anyMatch(existente -> seSuperponen(
                        solicitud.fechaInicio(), solicitud.fechaFin(),
                        existente.getFechaInicio(), existente.getFechaFin()));
        if (hayConflicto) {
            throw new ConflictoAsignacionException(usuario.getId());
        }

        Asignacion asignacion = asignacionRepository.save(Asignacion.builder()
                .usuario(usuario).proyecto(proyecto).ubicacion(ubicacion).horario(horario)
                .fechaInicio(solicitud.fechaInicio()).fechaFin(solicitud.fechaFin())
                .build());

        return AsignacionResponse.desde(asignacion, LocalDate.now(ZONA_HORARIA_PERU));
    }

    /** HU09: asignaciones vigentes o futuras del colaborador autenticado, más recientes primero. */
    @Transactional(readOnly = true)
    public List<AsignacionResponse> obtenerAgenda(Long usuarioId) {
        LocalDate hoy = LocalDate.now(ZONA_HORARIA_PERU);
        return asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(usuarioId).stream()
                .filter(asignacion -> asignacion.getFechaFin() == null || !asignacion.getFechaFin().isBefore(hoy))
                .map(asignacion -> AsignacionResponse.desde(asignacion, hoy))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AsignacionResponse> listarAsignaciones(Long usuarioIdFiltro) {
        LocalDate hoy = LocalDate.now(ZONA_HORARIA_PERU);
        List<Asignacion> asignaciones = usuarioIdFiltro != null
                ? asignacionRepository.findByUsuarioIdAndActivoTrueOrderByFechaInicioAsc(usuarioIdFiltro)
                : asignacionRepository.findAllByOrderByFechaInicioDesc();
        return asignaciones.stream().map(asignacion -> AsignacionResponse.desde(asignacion, hoy)).toList();
    }

    /** Dos rangos [inicioA,finA] y [inicioB,finB] (fin nulo = sin fecha de término) se cruzan. */
    private boolean seSuperponen(LocalDate inicioA, LocalDate finA, LocalDate inicioB, LocalDate finB) {
        boolean aEmpiezaAntesDeQueTermineB = finB == null || !inicioA.isAfter(finB);
        boolean bEmpiezaAntesDeQueTermineA = finA == null || !inicioB.isAfter(finA);
        return aEmpiezaAntesDeQueTermineB && bEmpiezaAntesDeQueTermineA;
    }
}
