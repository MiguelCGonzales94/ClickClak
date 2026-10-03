package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.DispositivoResponse;
import com.clickclak.backend.service.DispositivoService;

/** Dispositivos WebAuthn asociados al usuario autenticado. */
@RestController
@RequestMapping("/api/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivoService;

    public DispositivoController(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    @GetMapping("/mios")
    public List<DispositivoResponse> listarMios(Authentication authentication) {
        Long usuarioId = (Long) authentication.getPrincipal();
        return dispositivoService.listarActivosPorUsuario(usuarioId).stream()
                .map(DispositivoResponse::desde)
                .toList();
    }
}
