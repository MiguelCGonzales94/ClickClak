package com.clickclak.backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.EditarUbicacionRequest;
import com.clickclak.backend.dto.RegistrarUbicacionRequest;
import com.clickclak.backend.exception.OperacionNoPermitidaException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;

/**
 * HU06: alta, corrección y baja de sedes/zonas autorizadas dentro de un proyecto. La zona es un
 * centro más un radio de tolerancia; cada cambio queda en la bitácora. Desactivar es una baja
 * lógica y se rechaza mientras haya asignaciones en curso o programadas en esa sede.
 */
@Service
public class UbicacionService {

    private static final GeometryFactory FABRICA_GEOMETRIA = new GeometryFactory(new PrecisionModel(), 4326);
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final int RADIO_TOLERANCIA_METROS_POR_DEFECTO = 150;

    private final UbicacionRepository ubicacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public UbicacionService(UbicacionRepository ubicacionRepository, ProyectoRepository proyectoRepository,
                            AsignacionRepository asignacionRepository, UsuarioRepository usuarioRepository,
                            AuditoriaService auditoriaService) {
        this.ubicacionRepository = ubicacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public Ubicacion registrarUbicacion(RegistrarUbicacionRequest solicitud, Long actorId) {
        Proyecto proyecto = proyectoRepository.findById(solicitud.proyectoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + solicitud.proyectoId()));
        if (!proyecto.isActivo()) {
            throw new SolicitudInvalidaException("El servicio " + proyecto.getNombre() + " está desactivado: reactívelo para agregarle sedes");
        }

        Ubicacion ubicacion = ubicacionRepository.save(Ubicacion.builder()
                .proyecto(proyecto)
                .nombre(solicitud.nombre())
                .direccionReferencia(solicitud.direccionReferencia())
                .geom(punto(solicitud.latitud(), solicitud.longitud()))
                .radioToleranciaMetros(solicitud.radioToleranciaMetros() != null
                        ? solicitud.radioToleranciaMetros() : RADIO_TOLERANCIA_METROS_POR_DEFECTO)
                .build());

        auditar(actorId, ubicacion, AccionAuditoria.CREACION, null, capturarEstado(ubicacion));
        return ubicacion;
    }

    @Transactional
    public Ubicacion editarUbicacion(Long ubicacionId, EditarUbicacionRequest solicitud, Long actorId) {
        Ubicacion ubicacion = cargar(ubicacionId);
        Map<String, Object> anterior = capturarEstado(ubicacion);

        ubicacion.setNombre(solicitud.nombre());
        ubicacion.setDireccionReferencia(solicitud.direccionReferencia());
        ubicacion.setGeom(punto(solicitud.latitud(), solicitud.longitud()));
        ubicacion.setRadioToleranciaMetros(solicitud.radioToleranciaMetros());
        ubicacionRepository.save(ubicacion);

        auditar(actorId, ubicacion, AccionAuditoria.MODIFICACION, anterior, capturarEstado(ubicacion));
        return ubicacion;
    }

    /** Baja lógica. No se permite mientras haya técnicos asignados que aún la usan o la usarán. */
    @Transactional
    public Ubicacion desactivarUbicacion(Long ubicacionId, Long actorId) {
        Ubicacion ubicacion = cargar(ubicacionId);
        if (!ubicacion.isActivo()) {
            return ubicacion;
        }
        if (asignacionRepository.existeAsignacionVigenteOProgramada(ubicacionId, LocalDate.now(ZONA_LIMA))) {
            throw new OperacionNoPermitidaException(
                    "La sede tiene asignaciones en curso o programadas. Mueve o quita a esos técnicos antes de desactivarla");
        }
        Map<String, Object> anterior = capturarEstado(ubicacion);
        ubicacion.setActivo(false);
        ubicacionRepository.save(ubicacion);
        auditar(actorId, ubicacion, AccionAuditoria.MODIFICACION, anterior, capturarEstado(ubicacion));
        return ubicacion;
    }

    @Transactional
    public Ubicacion reactivarUbicacion(Long ubicacionId, Long actorId) {
        Ubicacion ubicacion = cargar(ubicacionId);
        if (ubicacion.isActivo()) {
            return ubicacion;
        }
        Map<String, Object> anterior = capturarEstado(ubicacion);
        ubicacion.setActivo(true);
        ubicacionRepository.save(ubicacion);
        auditar(actorId, ubicacion, AccionAuditoria.MODIFICACION, anterior, capturarEstado(ubicacion));
        return ubicacion;
    }

    /**
     * Borrado físico, solo si ninguna asignación (ni siquiera una quitada) usó la sede: las marcaciones
     * cuelgan de las asignaciones y borrarla rompería la trazabilidad. Con historial, la salida es desactivar.
     */
    @Transactional
    public void eliminarUbicacion(Long ubicacionId, Long actorId) {
        Ubicacion ubicacion = cargar(ubicacionId);
        if (asignacionRepository.existsByUbicacionId(ubicacionId)) {
            throw new OperacionNoPermitidaException(
                    "La sede tiene asignaciones registradas y no se puede eliminar: desactívela en su lugar");
        }
        Map<String, Object> anterior = capturarEstado(ubicacion);
        ubicacionRepository.delete(ubicacion);
        ubicacionRepository.flush();
        auditar(actorId, ubicacion, AccionAuditoria.ELIMINACION, anterior, null);
    }

    @Transactional(readOnly = true)
    public List<Ubicacion> listarUbicaciones(Long proyectoId) {
        return proyectoId != null ? ubicacionRepository.findByProyectoId(proyectoId) : ubicacionRepository.findAll();
    }

    private Ubicacion cargar(Long ubicacionId) {
        return ubicacionRepository.findById(ubicacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + ubicacionId));
    }

    private static Point punto(double latitud, double longitud) {
        return FABRICA_GEOMETRIA.createPoint(new Coordinate(longitud, latitud));
    }

    private Map<String, Object> capturarEstado(Ubicacion ubicacion) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("proyectoId", ubicacion.getProyecto().getId());
        estado.put("nombre", ubicacion.getNombre());
        estado.put("direccionReferencia", ubicacion.getDireccionReferencia());
        estado.put("latitud", ubicacion.getGeom().getY());
        estado.put("longitud", ubicacion.getGeom().getX());
        estado.put("radioToleranciaMetros", ubicacion.getRadioToleranciaMetros());
        estado.put("activo", ubicacion.isActivo());
        return estado;
    }

    private void auditar(Long actorId, Ubicacion ubicacion, AccionAuditoria accion,
                         Map<String, Object> anterior, Map<String, Object> nuevo) {
        Usuario actor = actorId != null ? usuarioRepository.getReferenceById(actorId) : null;
        auditoriaService.registrar(actor, "ubicacion", ubicacion.getId(), accion, anterior, nuevo);
    }
}
