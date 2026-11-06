package com.clickclak.backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.EditarProyectoRequest;
import com.clickclak.backend.dto.RegistrarProyectoRequest;
import com.clickclak.backend.exception.OperacionNoPermitidaException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;

/**
 * HU06: alta, corrección, baja y eliminación de proyectos (servicios/clientes) que agrupan sedes y
 * asignaciones. Desactivar es una baja lógica y se rechaza mientras haya asignaciones en curso o
 * programadas. Eliminar (borrado físico) solo se permite si el servicio nunca tuvo sedes ni
 * asignaciones: con historial, la salida es desactivarlo. Todo cambio queda en la bitácora.
 */
@Service
public class ProyectoService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String MENSAJE_CON_HISTORIAL =
            "El servicio tiene sedes o asignaciones registradas y no se puede eliminar: desactívelo en su lugar";

    private final ProyectoRepository proyectoRepository;
    private final UbicacionRepository ubicacionRepository;
    private final AsignacionRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public ProyectoService(ProyectoRepository proyectoRepository, UbicacionRepository ubicacionRepository,
                           AsignacionRepository asignacionRepository, UsuarioRepository usuarioRepository,
                           AuditoriaService auditoriaService) {
        this.proyectoRepository = proyectoRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public Proyecto registrarProyecto(RegistrarProyectoRequest solicitud, Long actorId) {
        validarFechas(solicitud.fechaInicio(), solicitud.fechaFin());
        Proyecto proyecto = proyectoRepository.save(Proyecto.builder()
                .nombre(solicitud.nombre().trim())
                .cliente(solicitud.cliente().trim())
                .fechaInicio(solicitud.fechaInicio())
                .fechaFin(solicitud.fechaFin())
                .build());
        auditar(actorId, proyecto.getId(), AccionAuditoria.CREACION, null, capturarEstado(proyecto));
        return proyecto;
    }

    @Transactional
    public Proyecto editarProyecto(Long proyectoId, EditarProyectoRequest solicitud, Long actorId) {
        Proyecto proyecto = cargar(proyectoId);
        validarFechas(solicitud.fechaInicio(), solicitud.fechaFin());
        Map<String, Object> anterior = capturarEstado(proyecto);

        proyecto.setNombre(solicitud.nombre().trim());
        proyecto.setCliente(solicitud.cliente().trim());
        proyecto.setFechaInicio(solicitud.fechaInicio());
        proyecto.setFechaFin(solicitud.fechaFin());
        proyectoRepository.save(proyecto);

        auditar(actorId, proyectoId, AccionAuditoria.MODIFICACION, anterior, capturarEstado(proyecto));
        return proyecto;
    }

    /** Baja lógica; no se permite mientras haya técnicos asignados que aún usan el servicio. */
    @Transactional
    public Proyecto desactivarProyecto(Long proyectoId, Long actorId) {
        Proyecto proyecto = cargar(proyectoId);
        if (!proyecto.isActivo()) {
            return proyecto;
        }
        if (asignacionRepository.existeAsignacionVigenteOProgramadaEnProyecto(proyectoId, LocalDate.now(ZONA_LIMA))) {
            throw new OperacionNoPermitidaException(
                    "El servicio tiene asignaciones en curso o programadas. Mueva o quite a esos técnicos antes de desactivarlo");
        }
        return cambiarActivo(proyecto, false, actorId);
    }

    @Transactional
    public Proyecto reactivarProyecto(Long proyectoId, Long actorId) {
        Proyecto proyecto = cargar(proyectoId);
        return proyecto.isActivo() ? proyecto : cambiarActivo(proyecto, true, actorId);
    }

    /** Borrado físico, solo si nunca tuvo sedes ni asignaciones (altas por error). */
    @Transactional
    public void eliminarProyecto(Long proyectoId, Long actorId) {
        Proyecto proyecto = cargar(proyectoId);
        if (ubicacionRepository.existsByProyectoId(proyectoId) || asignacionRepository.existsByProyectoId(proyectoId)) {
            throw new OperacionNoPermitidaException(MENSAJE_CON_HISTORIAL);
        }
        Map<String, Object> anterior = capturarEstado(proyecto);
        proyectoRepository.delete(proyecto);
        proyectoRepository.flush();
        auditar(actorId, proyectoId, AccionAuditoria.ELIMINACION, anterior, null);
    }

    @Transactional(readOnly = true)
    public List<Proyecto> listarProyectos() {
        return proyectoRepository.findAll();
    }

    private Proyecto cambiarActivo(Proyecto proyecto, boolean activo, Long actorId) {
        Map<String, Object> anterior = capturarEstado(proyecto);
        proyecto.setActivo(activo);
        proyectoRepository.save(proyecto);
        auditar(actorId, proyecto.getId(), AccionAuditoria.MODIFICACION, anterior, capturarEstado(proyecto));
        return proyecto;
    }

    private Proyecto cargar(Long proyectoId) {
        return proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado: " + proyectoId));
    }

    private static void validarFechas(LocalDate inicio, LocalDate fin) {
        if (fin != null && fin.isBefore(inicio)) {
            throw new SolicitudInvalidaException("La fecha de fin no puede ser anterior a la de inicio");
        }
    }

    private Map<String, Object> capturarEstado(Proyecto proyecto) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nombre", proyecto.getNombre());
        estado.put("cliente", proyecto.getCliente());
        estado.put("fechaInicio", proyecto.getFechaInicio().toString());
        estado.put("fechaFin", proyecto.getFechaFin() != null ? proyecto.getFechaFin().toString() : null);
        estado.put("activo", proyecto.isActivo());
        return estado;
    }

    private void auditar(Long actorId, Long proyectoId, AccionAuditoria accion,
                         Map<String, Object> anterior, Map<String, Object> nuevo) {
        Usuario actor = actorId != null ? usuarioRepository.getReferenceById(actorId) : null;
        auditoriaService.registrar(actor, "proyecto", proyectoId, accion, anterior, nuevo);
    }
}
