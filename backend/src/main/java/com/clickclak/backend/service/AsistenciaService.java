package com.clickclak.backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.AsistenciaResponse;
import com.clickclak.backend.dto.PaginaResponse;
import com.clickclak.backend.dto.ResumenAsistenciaResponse;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.TipoEvento;
import com.clickclak.backend.repository.EspecificacionesMarcacion;
import com.clickclak.backend.repository.MarcacionRepository;

/**
 * Consulta de asistencia para supervisión: lista paginada de marcaciones con filtros y un resumen
 * por estado de validación. Solo lee; registrar sigue siendo cosa de {@link MarcacionService}.
 *
 * <p>Las fechas del filtro son días de Lima, que es donde trabaja el personal: "2026-10-09" abarca
 * de las 00:00 a las 24:00 de Lima, no de UTC.
 */
@Service
public class AsistenciaService {

    static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    static final int TAMANO_MAXIMO_PAGINA = 100;

    /** Todos los campos son opcionales; sin ninguno se devuelve toda la asistencia, de la más reciente a la más antigua. */
    public record Filtro(
            Long usuarioId,
            Long proyectoId,
            Long ubicacionId,
            LocalDate desde,
            LocalDate hasta,
            EstadoValidacion estado,
            TipoEvento tipoEvento) {
    }

    private final MarcacionRepository marcacionRepository;

    public AsistenciaService(MarcacionRepository marcacionRepository) {
        this.marcacionRepository = marcacionRepository;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AsistenciaResponse> buscar(Filtro filtro, int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw new SolicitudInvalidaException(
                    "La página debe ser 0 o mayor y el tamaño debe estar entre 1 y " + TAMANO_MAXIMO_PAGINA);
        }
        Specification<Marcacion> especificacion =
                EspecificacionesMarcacion.conDetalle().and(filtrar(filtro, true));
        var orden = Sort.by(Sort.Order.desc("horaEvento"), Sort.Order.desc("id"));

        var resultado = marcacionRepository.findAll(especificacion, PageRequest.of(pagina, tamano, orden));
        return PaginaResponse.desde(resultado, AsistenciaResponse::desde);
    }

    /** El estado del filtro no se aplica: el resumen es justamente el reparto entre todos los estados. */
    @Transactional(readOnly = true)
    public ResumenAsistenciaResponse resumen(Filtro filtro) {
        Specification<Marcacion> base = filtrar(filtro, false);
        Map<EstadoValidacion, Long> porEstado = new EnumMap<>(EstadoValidacion.class);
        long total = 0;
        for (EstadoValidacion estado : EstadoValidacion.values()) {
            long cantidad = marcacionRepository.count(base.and(EspecificacionesMarcacion.conEstado(estado)));
            porEstado.put(estado, cantidad);
            total += cantidad;
        }
        return new ResumenAsistenciaResponse(total, porEstado);
    }

    private Specification<Marcacion> filtrar(Filtro filtro, boolean incluirEstado) {
        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw new SolicitudInvalidaException("La fecha inicial no puede ser posterior a la final");
        }
        Specification<Marcacion> especificacion = Specification.where(null);
        if (filtro.usuarioId() != null) {
            especificacion = especificacion.and(EspecificacionesMarcacion.deUsuario(filtro.usuarioId()));
        }
        if (filtro.proyectoId() != null) {
            especificacion = especificacion.and(EspecificacionesMarcacion.deProyecto(filtro.proyectoId()));
        }
        if (filtro.ubicacionId() != null) {
            especificacion = especificacion.and(EspecificacionesMarcacion.deUbicacion(filtro.ubicacionId()));
        }
        if (filtro.desde() != null) {
            especificacion = especificacion.and(
                    EspecificacionesMarcacion.desde(filtro.desde().atStartOfDay(ZONA_LIMA).toInstant()));
        }
        if (filtro.hasta() != null) {
            especificacion = especificacion.and(
                    EspecificacionesMarcacion.hasta(filtro.hasta().plusDays(1).atStartOfDay(ZONA_LIMA).toInstant()));
        }
        if (incluirEstado && filtro.estado() != null) {
            especificacion = especificacion.and(EspecificacionesMarcacion.conEstado(filtro.estado()));
        }
        if (filtro.tipoEvento() != null) {
            especificacion = especificacion.and(EspecificacionesMarcacion.deTipo(filtro.tipoEvento()));
        }
        return especificacion;
    }
}
