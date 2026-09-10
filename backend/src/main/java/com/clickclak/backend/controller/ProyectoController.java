package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.ProyectoResponse;
import com.clickclak.backend.dto.RegistrarProyectoRequest;
import com.clickclak.backend.service.ProyectoService;

import jakarta.validation.Valid;

/** HU06: proyectos (servicios/clientes) que agrupan las sedes autorizadas. */
@RestController
@RequestMapping("/api/proyectos")
public class ProyectoController {

    private final ProyectoService proyectoService;

    public ProyectoController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<ProyectoResponse> registrar(@Valid @RequestBody RegistrarProyectoRequest solicitud) {
        var proyecto = proyectoService.registrarProyecto(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProyectoResponse.desde(proyecto));
    }

    @GetMapping
    public List<ProyectoResponse> listar() {
        return proyectoService.listarProyectos().stream().map(ProyectoResponse::desde).toList();
    }
}
