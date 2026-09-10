package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.clickclak.backend.dto.RegistrarHorarioRequest;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.repository.HorarioRepository;

@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    @Mock private HorarioRepository horarioRepository;

    private HorarioService horarioService;

    @BeforeEach
    void configurar() {
        horarioService = new HorarioService(horarioRepository);
    }

    @Test
    void horaFinAnteriorAHoraInicio_lanzaExcepcion() {
        var solicitud = new RegistrarHorarioRequest(
                "Turno inválido", LocalTime.of(18, 0), LocalTime.of(8, 0), null, null, 10, null);

        assertThatThrownBy(() -> horarioService.registrarHorario(solicitud))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void horaFinIgualAHoraInicio_lanzaExcepcion() {
        var solicitud = new RegistrarHorarioRequest(
                "Turno inválido", LocalTime.of(8, 0), LocalTime.of(8, 0), null, null, 10, null);

        assertThatThrownBy(() -> horarioService.registrarHorario(solicitud))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void solicitudValidaSinToleranciaNiDias_aplicaValoresPorDefecto() {
        when(horarioRepository.save(any(Horario.class))).thenAnswer(inv -> inv.getArgument(0));

        var solicitud = new RegistrarHorarioRequest(
                "Turno diurno", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, null, null);

        Horario resultado = horarioService.registrarHorario(solicitud);

        assertThat(resultado.getToleranciaMinutos()).isEqualTo(10);
        assertThat(resultado.getDiasSemana()).isEqualTo("L,M,X,J,V");
    }
}
