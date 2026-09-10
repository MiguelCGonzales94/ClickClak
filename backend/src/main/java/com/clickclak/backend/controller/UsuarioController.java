package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.EditarUsuarioRequest;
import com.clickclak.backend.dto.RegistrarUsuarioRequest;
import com.clickclak.backend.dto.UsuarioResponse;
import com.clickclak.backend.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * HU04: gestión de usuarios y roles. Crear/editar/activar/desactivar es exclusivo de
 * RRHH_ADMIN ("Como Administrador" en la HU); el listado también lo puede usar un
 * Supervisor porque lo necesita para elegir técnicos al asignarlos (HU08) o enrolar su
 * dispositivo (HU05) — ver ese hueco señalado al cerrar HU05.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public ResponseEntity<UsuarioResponse> registrar(
            @Valid @RequestBody RegistrarUsuarioRequest solicitud, Authentication authentication) {
        var usuario = usuarioService.registrarUsuario(solicitud, idAutenticado(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public UsuarioResponse editar(
            @PathVariable Long id, @Valid @RequestBody EditarUsuarioRequest solicitud, Authentication authentication) {
        return usuarioService.editarUsuario(id, solicitud, idAutenticado(authentication));
    }

    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public UsuarioResponse activar(@PathVariable Long id, Authentication authentication) {
        return usuarioService.cambiarEstado(id, true, idAutenticado(authentication));
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public UsuarioResponse desactivar(@PathVariable Long id, Authentication authentication) {
        return usuarioService.cambiarEstado(id, false, idAutenticado(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RRHH_ADMIN', 'SUPERVISOR')")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarioService.obtenerPorId(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RRHH_ADMIN', 'SUPERVISOR')")
    public List<UsuarioResponse> listar(
            @RequestParam(required = false) String rol, @RequestParam(required = false) Boolean activo) {
        return usuarioService.listar(rol, activo);
    }

    private Long idAutenticado(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
