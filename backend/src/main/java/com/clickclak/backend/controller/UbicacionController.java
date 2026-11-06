package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.EditarUbicacionRequest;
import com.clickclak.backend.dto.RegistrarUbicacionRequest;
import com.clickclak.backend.dto.UbicacionResponse;
import com.clickclak.backend.service.UbicacionService;

import jakarta.validation.Valid;

/** HU06: sedes y zonas autorizadas dentro de un proyecto (alta, corrección y baja lógica). */
@RestController
@RequestMapping("/api/ubicaciones")
public class UbicacionController {

    private final UbicacionService ubicacionService;

    public UbicacionController(UbicacionService ubicacionService) {
        this.ubicacionService = ubicacionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<UbicacionResponse> registrar(
            @Valid @RequestBody RegistrarUbicacionRequest solicitud, Authentication authentication) {
        var ubicacion = ubicacionService.registrarUbicacion(solicitud, idAutenticado(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(UbicacionResponse.desde(ubicacion));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public List<UbicacionResponse> listar(@RequestParam(required = false) Long proyectoId) {
        return ubicacionService.listarUbicaciones(proyectoId).stream().map(UbicacionResponse::desde).toList();
    }

    /** Corrige nombre, referencia y zona (centro y radio) de una sede. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public UbicacionResponse editar(
            @PathVariable Long id, @Valid @RequestBody EditarUbicacionRequest solicitud, Authentication authentication) {
        return UbicacionResponse.desde(ubicacionService.editarUbicacion(id, solicitud, idAutenticado(authentication)));
    }

    /** Borrado físico, solo si ninguna asignación usó la sede; si no, 409 y se ofrece desactivar. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        ubicacionService.eliminarUbicacion(id, idAutenticado(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Baja lógica; 409 si la sede tiene asignaciones en curso o programadas. */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public UbicacionResponse desactivar(@PathVariable Long id, Authentication authentication) {
        return UbicacionResponse.desde(ubicacionService.desactivarUbicacion(id, idAutenticado(authentication)));
    }

    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public UbicacionResponse reactivar(@PathVariable Long id, Authentication authentication) {
        return UbicacionResponse.desde(ubicacionService.reactivarUbicacion(id, idAutenticado(authentication)));
    }

    private Long idAutenticado(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
