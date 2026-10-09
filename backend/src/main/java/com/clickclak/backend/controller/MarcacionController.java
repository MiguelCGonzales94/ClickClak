package com.clickclak.backend.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.AsistenciaResponse;
import com.clickclak.backend.dto.MarcacionResponse;
import com.clickclak.backend.dto.PaginaResponse;
import com.clickclak.backend.dto.RegistrarMarcacionRequest;
import com.clickclak.backend.dto.ResumenAsistenciaResponse;
import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.TipoEvento;
import com.clickclak.backend.service.AsistenciaService;
import com.clickclak.backend.service.MarcacionService;

import jakarta.validation.Valid;

/**
 * HU10/HU11: registro de ingreso y salida geolocalizado del personal de campo. La consulta de
 * asistencia (lista y resumen) es de supervisión: SUPERVISOR y RRHH_ADMIN, igual que las
 * incidencias, y ven la asistencia de todo el personal. Un colaborador no consulta aquí.
 */
@RestController
@RequestMapping("/api/marcaciones")
public class MarcacionController {

    private final MarcacionService marcacionService;
    private final AsistenciaService asistenciaService;

    public MarcacionController(MarcacionService marcacionService, AsistenciaService asistenciaService) {
        this.marcacionService = marcacionService;
        this.asistenciaService = asistenciaService;
    }

    /**
     * El id de usuario autenticado manda sobre el del cuerpo: un colaborador solo puede
     * registrar su propia asistencia, nunca la de otro alterando el JSON de la solicitud.
     */
    @PostMapping
    public ResponseEntity<MarcacionResponse> registrar(
            @Valid @RequestBody RegistrarMarcacionRequest solicitud, Authentication authentication) {

        Long usuarioAutenticadoId = (Long) authentication.getPrincipal();
        if (!usuarioAutenticadoId.equals(solicitud.usuarioId())) {
            throw new AccessDeniedException("No puede registrar la marcación de otro usuario");
        }

        Marcacion marcacion = marcacionService.registrarMarcacion(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(MarcacionResponse.desde(marcacion));
    }

    /** Fechas en formato AAAA-MM-DD, tomadas como días de Lima; ambas inclusivas. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public PaginaResponse<AsistenciaResponse> listar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoValidacion estado,
            @RequestParam(required = false) TipoEvento tipoEvento,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var filtro = new AsistenciaService.Filtro(usuarioId, proyectoId, ubicacionId, desde, hasta, estado, tipoEvento);
        return asistenciaService.buscar(filtro, pagina, tamano);
    }

    /** El reparto por estado con los mismos filtros que la lista, sin el de estado. */
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResumenAsistenciaResponse resumen(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoEvento tipoEvento) {
        var filtro = new AsistenciaService.Filtro(usuarioId, proyectoId, ubicacionId, desde, hasta, null, tipoEvento);
        return asistenciaService.resumen(filtro);
    }
}
