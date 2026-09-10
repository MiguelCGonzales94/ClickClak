package com.clickclak.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.RegistrarProyectoRequest;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.repository.ProyectoRepository;

/** HU06: alta y consulta de proyectos (servicios/clientes) que agrupan sedes y asignaciones. */
@Service
public class ProyectoService {

    private final ProyectoRepository proyectoRepository;

    public ProyectoService(ProyectoRepository proyectoRepository) {
        this.proyectoRepository = proyectoRepository;
    }

    @Transactional
    public Proyecto registrarProyecto(RegistrarProyectoRequest solicitud) {
        return proyectoRepository.save(Proyecto.builder()
                .nombre(solicitud.nombre())
                .cliente(solicitud.cliente())
                .fechaInicio(solicitud.fechaInicio())
                .fechaFin(solicitud.fechaFin())
                .build());
    }

    public List<Proyecto> listarProyectos() {
        return proyectoRepository.findAll();
    }
}
