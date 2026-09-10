package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.RegistrarUbicacionRequest;
import com.clickclak.backend.dto.UbicacionResponse;
import com.clickclak.backend.service.UbicacionService;

import jakarta.validation.Valid;

/** HU06: sedes y zonas autorizadas dentro de un proyecto. */
@RestController
@RequestMapping("/api/ubicaciones")
public class UbicacionController {

    private final UbicacionService ubicacionService;

    public UbicacionController(UbicacionService ubicacionService) {
        this.ubicacionService = ubicacionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<UbicacionResponse> registrar(@Valid @RequestBody RegistrarUbicacionRequest solicitud) {
        var ubicacion = ubicacionService.registrarUbicacion(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(UbicacionResponse.desde(ubicacion));
    }

    @GetMapping
    public List<UbicacionResponse> listar(@RequestParam(required = false) Long proyectoId) {
        return ubicacionService.listarUbicaciones(proyectoId).stream().map(UbicacionResponse::desde).toList();
    }
}
