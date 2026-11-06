package com.clickclak.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.DireccionResponse;
import com.clickclak.backend.service.GeocodificacionService;

/** Dirección aproximada de un punto, para que supervisión entienda dónde cae una zona o una marcación. */
@RestController
@RequestMapping("/api/geocodificacion")
public class GeocodificacionController {

    private final GeocodificacionService geocodificacionService;

    public GeocodificacionController(GeocodificacionService geocodificacionService) {
        this.geocodificacionService = geocodificacionService;
    }

    @GetMapping("/inversa")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public DireccionResponse inversa(@RequestParam double latitud, @RequestParam double longitud) {
        return geocodificacionService.direccionDe(latitud, longitud);
    }
}
