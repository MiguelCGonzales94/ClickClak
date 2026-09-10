package com.clickclak.backend.service;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.RegistrarUbicacionRequest;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.UbicacionRepository;

/** HU06: alta y consulta de sedes/zonas autorizadas dentro de un proyecto. */
@Service
public class UbicacionService {

    private static final GeometryFactory FABRICA_GEOMETRIA = new GeometryFactory(new PrecisionModel(), 4326);
    private static final int RADIO_TOLERANCIA_METROS_POR_DEFECTO = 150;

    private final UbicacionRepository ubicacionRepository;
    private final ProyectoRepository proyectoRepository;

    public UbicacionService(UbicacionRepository ubicacionRepository, ProyectoRepository proyectoRepository) {
        this.ubicacionRepository = ubicacionRepository;
        this.proyectoRepository = proyectoRepository;
    }

    @Transactional
    public Ubicacion registrarUbicacion(RegistrarUbicacionRequest solicitud) {
        Proyecto proyecto = proyectoRepository.findById(solicitud.proyectoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + solicitud.proyectoId()));

        Point punto = FABRICA_GEOMETRIA.createPoint(new Coordinate(solicitud.longitud(), solicitud.latitud()));

        return ubicacionRepository.save(Ubicacion.builder()
                .proyecto(proyecto)
                .nombre(solicitud.nombre())
                .direccionReferencia(solicitud.direccionReferencia())
                .geom(punto)
                .radioToleranciaMetros(solicitud.radioToleranciaMetros() != null
                        ? solicitud.radioToleranciaMetros() : RADIO_TOLERANCIA_METROS_POR_DEFECTO)
                .build());
    }

    public List<Ubicacion> listarUbicaciones(Long proyectoId) {
        return proyectoId != null ? ubicacionRepository.findByProyectoId(proyectoId) : ubicacionRepository.findAll();
    }
}
