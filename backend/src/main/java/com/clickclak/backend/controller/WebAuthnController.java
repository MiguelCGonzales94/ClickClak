package com.clickclak.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.FinalizarAutenticacionWebAuthnRequest;
import com.clickclak.backend.dto.FinalizarRegistroWebAuthnRequest;
import com.clickclak.backend.dto.IniciarAutenticacionWebAuthnRequest;
import com.clickclak.backend.dto.IniciarRegistroWebAuthnRequest;
import com.clickclak.backend.dto.LoginResponse;
import com.clickclak.backend.dto.OpcionesWebAuthnResponse;
import com.clickclak.backend.service.WebAuthnService;

import jakarta.validation.Valid;

/**
 * HU05: enrolamiento y autenticación por WebAuthn.
 *
 * <p>El enrolamiento ("registro") lo dispara un Supervisor/RRHH autenticado con el técnico
 * físicamente presente — evita que cualquiera registre un dispositivo a nombre de otro
 * usuario solo conociendo su correo corporativo. La autenticación es pública: es el propio
 * mecanismo de login, no hay JWT previo que exigir.
 */
@RestController
@RequestMapping("/api/webauthn")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    public WebAuthnController(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    @PostMapping("/registro/iniciar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public OpcionesWebAuthnResponse iniciarRegistro(@Valid @RequestBody IniciarRegistroWebAuthnRequest solicitud) {
        return webAuthnService.iniciarRegistro(solicitud.usuarioId());
    }

    @PostMapping("/registro/finalizar")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<Void> finalizarRegistro(@Valid @RequestBody FinalizarRegistroWebAuthnRequest solicitud) {
        webAuthnService.finalizarRegistro(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/autenticacion/iniciar")
    public OpcionesWebAuthnResponse iniciarAutenticacion(@Valid @RequestBody IniciarAutenticacionWebAuthnRequest solicitud) {
        return webAuthnService.iniciarAutenticacion(solicitud);
    }

    @PostMapping("/autenticacion/finalizar")
    public LoginResponse finalizarAutenticacion(@Valid @RequestBody FinalizarAutenticacionWebAuthnRequest solicitud) {
        return webAuthnService.finalizarAutenticacion(solicitud);
    }
}
