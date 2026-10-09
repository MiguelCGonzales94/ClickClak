package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.CacheControl;
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

import com.clickclak.backend.dto.ClaveTemporalResponse;
import com.clickclak.backend.dto.DesactivarUsuarioRequest;
import com.clickclak.backend.dto.EditarUsuarioRequest;
import com.clickclak.backend.dto.HistorialUsuarioResponse;
import com.clickclak.backend.dto.PaginaResponse;
import com.clickclak.backend.dto.RegistrarUsuarioRequest;
import com.clickclak.backend.dto.UsuarioResponse;
import com.clickclak.backend.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * HU04: gestión de usuarios y roles. Crear/editar/activar/desactivar/eliminar es exclusivo de
 * RRHH_ADMIN ("Como Administrador" en la HU); el listado simple también lo puede usar un
 * Supervisor porque lo necesita para elegir técnicos al asignarlos (HU08) o enrolar su
 * dispositivo (HU05) — ver ese hueco señalado al cerrar HU05. La búsqueda paginada y el
 * historial son del panel de administración y por eso solo los ve RRHH_ADMIN.
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

    /** El cuerpo con el motivo es opcional: sin cuerpo la baja se registra sin motivo. */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public UsuarioResponse desactivar(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DesactivarUsuarioRequest solicitud,
            Authentication authentication) {
        String motivo = solicitud != null ? solicitud.motivo() : null;
        return usuarioService.cambiarEstado(id, false, motivo, idAutenticado(authentication));
    }

    @PostMapping("/{id}/desbloquear")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public UsuarioResponse desbloquear(@PathVariable Long id, Authentication authentication) {
        return usuarioService.desbloquear(id, idAutenticado(authentication));
    }

    /** La clave temporal viaja una sola vez en la respuesta: por eso {@code no-store}, para que nada la guarde en caché. */
    @PostMapping("/{id}/restablecer-clave")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public ResponseEntity<ClaveTemporalResponse> restablecerClave(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(usuarioService.restablecerClave(id, idAutenticado(authentication)));
    }

    /** 204 si se borró; 409 si el usuario tiene historial (la salida es desactivarlo). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        usuarioService.eliminarUsuario(id, idAutenticado(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public PaginaResponse<UsuarioResponse> buscar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return usuarioService.buscar(q, rol, estado, pagina, tamano);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasRole('RRHH_ADMIN')")
    public List<HistorialUsuarioResponse> historial(@PathVariable Long id) {
        return usuarioService.historial(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RRHH_ADMIN', 'SUPERVISOR')")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarioService.obtenerPorId(id);
    }

    /** Lista completa sin paginar: la usan Asignaciones, Dashboard, Incidencias y la app de campo. */
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
