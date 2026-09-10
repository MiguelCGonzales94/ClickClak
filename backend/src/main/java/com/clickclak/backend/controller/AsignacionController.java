package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.AsignacionResponse;
import com.clickclak.backend.dto.RegistrarAsignacionRequest;
import com.clickclak.backend.service.AsignacionService;

import jakarta.validation.Valid;

/** HU08: asignación de técnicos a proyectos/sedes/turnos. HU09: agenda propia del colaborador. */
@RestController
@RequestMapping("/api/asignaciones")
public class AsignacionController {

    private final AsignacionService asignacionService;

    public AsignacionController(AsignacionService asignacionService) {
        this.asignacionService = asignacionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<AsignacionResponse> registrar(@Valid @RequestBody RegistrarAsignacionRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asignacionService.registrarAsignacion(solicitud));
    }

    /** Vista de supervisión: todas las asignaciones, o las de un colaborador puntual. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public List<AsignacionResponse> listar(@RequestParam(required = false) Long usuarioId) {
        return asignacionService.listarAsignaciones(usuarioId);
    }

    /** HU09: cada colaborador consulta únicamente su propia agenda — nunca la de otro. */
    @GetMapping("/mias")
    public List<AsignacionResponse> misAsignaciones(Authentication authentication) {
        Long usuarioAutenticadoId = (Long) authentication.getPrincipal();
        return asignacionService.obtenerAgenda(usuarioAutenticadoId);
    }
}
