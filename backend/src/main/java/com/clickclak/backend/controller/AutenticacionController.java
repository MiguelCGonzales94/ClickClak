package com.clickclak.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.LoginRequest;
import com.clickclak.backend.dto.LoginResponse;
import com.clickclak.backend.dto.MensajeResponse;
import com.clickclak.backend.dto.PerfilResponse;
import com.clickclak.backend.dto.RestablecerClaveRequest;
import com.clickclak.backend.dto.SolicitarRecuperacionRequest;
import com.clickclak.backend.service.AutenticacionService;
import com.clickclak.backend.service.RecuperacionClaveService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final AutenticacionService autenticacionService;
    private final RecuperacionClaveService recuperacionClaveService;

    public AutenticacionController(AutenticacionService autenticacionService, RecuperacionClaveService recuperacionClaveService) {
        this.autenticacionService = autenticacionService;
        this.recuperacionClaveService = recuperacionClaveService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest solicitud) {
        return autenticacionService.autenticar(solicitud);
    }

    /** El {@link JwtAuthenticationFilter} deja el id del usuario como principal del token JWT. */
    @GetMapping("/yo")
    public PerfilResponse yo(Authentication authentication) {
        Long usuarioId = (Long) authentication.getPrincipal();
        return autenticacionService.obtenerPerfil(usuarioId);
    }

    /**
     * HU02: requiere estar autenticado (no tendría sentido "cerrar" una sesión que no existe)
     * — por eso no está en la lista de rutas públicas de SecurityConfig.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String cabeceraAutorizacion) {
        autenticacionService.cerrarSesion(cabeceraAutorizacion.substring(7));
        return ResponseEntity.noContent().build();
    }

    /** HU03: público — quien todavía no puede iniciar sesión es exactamente quien lo necesita. */
    @PostMapping("/recuperacion/solicitar")
    public MensajeResponse solicitarRecuperacion(@Valid @RequestBody SolicitarRecuperacionRequest solicitud) {
        return recuperacionClaveService.solicitar(solicitud);
    }

    @PostMapping("/recuperacion/restablecer")
    public MensajeResponse restablecerClave(@Valid @RequestBody RestablecerClaveRequest solicitud) {
        return recuperacionClaveService.restablecer(solicitud);
    }
}
