package com.clickclak.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.RegistrarHorarioRequest;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.repository.HorarioRepository;

/** HU07: alta y consulta de turnos de trabajo. */
@Service
public class HorarioService {

    private static final int TOLERANCIA_MINUTOS_POR_DEFECTO = 10;
    private static final String DIAS_SEMANA_POR_DEFECTO = "L,M,X,J,V";

    private final HorarioRepository horarioRepository;

    public HorarioService(HorarioRepository horarioRepository) {
        this.horarioRepository = horarioRepository;
    }

    @Transactional
    public Horario registrarHorario(RegistrarHorarioRequest solicitud) {
        if (!solicitud.horaFin().isAfter(solicitud.horaInicio())) {
            throw new SolicitudInvalidaException("La hora de fin debe ser posterior a la hora de inicio");
        }

        return horarioRepository.save(Horario.builder()
                .nombre(solicitud.nombre())
                .horaInicio(solicitud.horaInicio())
                .horaFin(solicitud.horaFin())
                .horaInicioRefrigerio(solicitud.horaInicioRefrigerio())
                .horaFinRefrigerio(solicitud.horaFinRefrigerio())
                .toleranciaMinutos(solicitud.toleranciaMinutos() != null
                        ? solicitud.toleranciaMinutos() : TOLERANCIA_MINUTOS_POR_DEFECTO)
                .diasSemana(solicitud.diasSemana() != null ? solicitud.diasSemana() : DIAS_SEMANA_POR_DEFECTO)
                .build());
    }

    public List<Horario> listarHorarios() {
        return horarioRepository.findAll();
    }
}
