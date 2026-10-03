package com.clickclak.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.dto.HorarioResponse;
import com.clickclak.backend.dto.RegistrarHorarioRequest;
import com.clickclak.backend.service.HorarioService;

import jakarta.validation.Valid;

/** HU07: turnos y horarios de trabajo del personal de campo. */
@RestController
@RequestMapping("/api/horarios")
public class HorarioController {

    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public ResponseEntity<HorarioResponse> registrar(@Valid @RequestBody RegistrarHorarioRequest solicitud) {
        var horario = horarioService.registrarHorario(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(HorarioResponse.desde(horario));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'RRHH_ADMIN')")
    public List<HorarioResponse> listar() {
        return horarioService.listarHorarios().stream().map(HorarioResponse::desde).toList();
    }
}
