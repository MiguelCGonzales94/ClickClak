package com.clickclak.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.MarcacionResponse;
import com.clickclak.backend.dto.RegistrarMarcacionRequest;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.service.MarcacionService;

import jakarta.validation.Valid;

/** HU10/HU11: registro de ingreso y salida geolocalizado del personal de campo. */
@RestController
@RequestMapping("/api/marcaciones")
public class MarcacionController {

    private final MarcacionService marcacionService;

    public MarcacionController(MarcacionService marcacionService) {
        this.marcacionService = marcacionService;
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
}
