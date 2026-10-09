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

import com.clickclak.backend.dto.AsignacionResponse;
import com.clickclak.backend.dto.EditarAsignacionRequest;
import com.clickclak.backend.dto.MoverAsignacionRequest;
import com.clickclak.backend.dto.RegistrarAsignacionRequest;
import com.clickclak.backend.service.AsignacionService;

import jakarta.validation.Valid;

/**
 * HU08: asignación de técnicos a proyectos/sedes/turnos (crear, editar, quitar y mover de sede; un
 * técnico puede tener varias sedes a la vez). HU09: agenda propia del colaborador.
 */
@RestController
@RequestMapping("/api/asignaciones")
public class AsignacionController {

    private final AsignacionService asignacionService;

    public AsignacionController(AsignacionService asignacionService) {
        this.asignacionService = asignacionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<AsignacionResponse> registrar(
            @Valid @RequestBody RegistrarAsignacionRequest solicitud, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asignacionService.registrarAsignacion(solicitud, idAutenticado(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public AsignacionResponse editar(
            @PathVariable Long id, @Valid @RequestBody EditarAsignacionRequest solicitud, Authentication authentication) {
        return asignacionService.editarAsignacion(id, solicitud, idAutenticado(authentication));
    }

    /** Baja lógica: deja de contar para validar marcaciones; las ya hechas conservan su asignación. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<Void> quitar(@PathVariable Long id, Authentication authentication) {
        asignacionService.quitarAsignacion(id, idAutenticado(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Termina la asignación el día anterior y crea la nueva en la otra sede; devuelve la nueva. */
    @PostMapping("/{id}/mover")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<AsignacionResponse> mover(
            @PathVariable Long id, @Valid @RequestBody MoverAsignacionRequest solicitud, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asignacionService.moverAsignacion(id, solicitud, idAutenticado(authentication)));
    }

    /** Vista de supervisión: todas las asignaciones activas, o las de un colaborador puntual. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public List<AsignacionResponse> listar(@RequestParam(required = false) Long usuarioId) {
        return asignacionService.listarAsignaciones(usuarioId);
    }

    /** HU09: cada colaborador consulta únicamente su propia agenda — nunca la de otro. */
    @GetMapping("/mias")
    public List<AsignacionResponse> misAsignaciones(Authentication authentication) {
        return asignacionService.obtenerAgenda(idAutenticado(authentication));
    }

    private Long idAutenticado(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
