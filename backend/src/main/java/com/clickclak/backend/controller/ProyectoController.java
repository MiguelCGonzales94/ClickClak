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
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.EditarProyectoRequest;
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
    public ResponseEntity<ProyectoResponse> registrar(
            @Valid @RequestBody RegistrarProyectoRequest solicitud, Authentication authentication) {
        var proyecto = proyectoService.registrarProyecto(solicitud, idAutenticado(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProyectoResponse.desde(proyecto));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public List<ProyectoResponse> listar() {
        return proyectoService.listarProyectos().stream().map(ProyectoResponse::desde).toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ProyectoResponse editar(
            @PathVariable Long id, @Valid @RequestBody EditarProyectoRequest solicitud, Authentication authentication) {
        return ProyectoResponse.desde(proyectoService.editarProyecto(id, solicitud, idAutenticado(authentication)));
    }

    /** Baja lógica; 409 si el servicio tiene asignaciones en curso o programadas. */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ProyectoResponse desactivar(@PathVariable Long id, Authentication authentication) {
        return ProyectoResponse.desde(proyectoService.desactivarProyecto(id, idAutenticado(authentication)));
    }

    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ProyectoResponse reactivar(@PathVariable Long id, Authentication authentication) {
        return ProyectoResponse.desde(proyectoService.reactivarProyecto(id, idAutenticado(authentication)));
    }

    /** Borrado físico, solo si nunca tuvo sedes ni asignaciones; si no, 409 y se ofrece desactivar. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        proyectoService.eliminarProyecto(id, idAutenticado(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long idAutenticado(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
