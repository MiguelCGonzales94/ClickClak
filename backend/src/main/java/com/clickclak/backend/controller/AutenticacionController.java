package com.clickclak.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.CambiarClaveRequest;
import com.clickclak.backend.dto.LoginRequest;
import com.clickclak.backend.dto.LoginResponse;
import com.clickclak.backend.dto.MensajeResponse;
import com.clickclak.backend.dto.PerfilResponse;
import com.clickclak.backend.dto.RestablecerClaveRequest;
import com.clickclak.backend.dto.SolicitarRecuperacionRequest;
import com.clickclak.backend.service.AutenticacionService;
import com.clickclak.backend.service.CambioClaveService;
import com.clickclak.backend.service.RecuperacionClaveService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final AutenticacionService autenticacionService;
    private final RecuperacionClaveService recuperacionClaveService;
    private final CambioClaveService cambioClaveService;

    public AutenticacionController(
            AutenticacionService autenticacionService,
            RecuperacionClaveService recuperacionClaveService,
            CambioClaveService cambioClaveService) {
        this.autenticacionService = autenticacionService;
        this.recuperacionClaveService = recuperacionClaveService;
        this.cambioClaveService = cambioClaveService;
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

    /**
     * HU04: cambio de la propia contraseña, también el obligatorio tras un restablecimiento del
     * administrador. Es de los pocos endpoints que el filtro deja pasar con clave pendiente.
     */
    @PostMapping("/cambiar-clave")
    public MensajeResponse cambiarClave(
            @Valid @RequestBody CambiarClaveRequest solicitud,
            Authentication authentication,
            @RequestHeader("Authorization") String cabeceraAutorizacion) {
        return cambioClaveService.cambiarClave(
                (Long) authentication.getPrincipal(), cabeceraAutorizacion.substring(7), solicitud);
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
