package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.IncidenciaDetalleResponse;
import com.clickclak.backend.dto.IncidenciaResponse;
import com.clickclak.backend.dto.RegistrarIncidenciaRequest;
import com.clickclak.backend.dto.ResolverIncidenciaRequest;
import com.clickclak.backend.model.EstadoIncidencia;
import com.clickclak.backend.service.IncidenciaService;

import jakarta.validation.Valid;

/**
 * Incidencias laborales: el colaborador registra y consulta las suyas; supervisores y RRHH
 * revisan. Este controlador solo traduce HTTP y exige el rol de las operaciones de revisión;
 * las reglas de propiedad y de separación de funciones viven en {@link IncidenciaService}.
 */
@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    private final IncidenciaService incidenciaService;

    public IncidenciaController(IncidenciaService incidenciaService) {
        this.incidenciaService = incidenciaService;
    }

    @PostMapping
    public ResponseEntity<IncidenciaResponse> registrar(
            @Valid @RequestBody RegistrarIncidenciaRequest solicitud, Authentication authentication) {
        IncidenciaResponse creada = incidenciaService.registrar(usuarioAutenticado(authentication), solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /** Cada colaborador consulta únicamente sus propias incidencias. */
    @GetMapping("/mias")
    public List<IncidenciaResponse> mias(
            @RequestParam(required = false) EstadoIncidencia estado, Authentication authentication) {
        return incidenciaService.listarPropias(usuarioAutenticado(authentication), estado);
    }

    /** Bandeja de supervisión, filtrable por colaborador y por estado. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public List<IncidenciaResponse> listar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) EstadoIncidencia estado) {
        return incidenciaService.listar(usuarioId, estado);
    }

    @GetMapping("/{id}")
    public IncidenciaDetalleResponse obtener(@PathVariable Long id, Authentication authentication) {
        return incidenciaService.obtener(usuarioAutenticado(authentication), id);
    }

    @PostMapping("/{id}/iniciar-revision")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public IncidenciaResponse iniciarRevision(
            @PathVariable Long id, @Valid @RequestBody(required = false) ResolverIncidenciaRequest cuerpo,
            Authentication authentication) {
        return incidenciaService.iniciarRevision(usuarioAutenticado(authentication), id, comentarioDe(cuerpo));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public IncidenciaResponse aprobar(
            @PathVariable Long id, @Valid @RequestBody(required = false) ResolverIncidenciaRequest cuerpo,
            Authentication authentication) {
        return incidenciaService.aprobar(usuarioAutenticado(authentication), id, comentarioDe(cuerpo));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public IncidenciaResponse rechazar(
            @PathVariable Long id, @Valid @RequestBody(required = false) ResolverIncidenciaRequest cuerpo,
            Authentication authentication) {
        return incidenciaService.rechazar(usuarioAutenticado(authentication), id, comentarioDe(cuerpo));
    }

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public IncidenciaResponse cerrar(
            @PathVariable Long id, @Valid @RequestBody(required = false) ResolverIncidenciaRequest cuerpo,
            Authentication authentication) {
        return incidenciaService.cerrar(usuarioAutenticado(authentication), id, comentarioDe(cuerpo));
    }

    /** El filtro JWT deja el id del usuario como principal del token. */
    private Long usuarioAutenticado(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    private String comentarioDe(ResolverIncidenciaRequest cuerpo) {
        return cuerpo == null ? null : cuerpo.comentario();
    }
}
