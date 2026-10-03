package com.clickclak.backend.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica contra el Postgres real las restricciones de la migración V2: son la última línea
 * de defensa de la integridad cuando un servicio falla o alguien escribe directo en la base.
 */
@SpringBootTest
@Transactional
class RestriccionesBaseDatosTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Long usuarioId;
    private Long proyectoId;
    private Long ubicacionId;
    private Long horarioId;

    @BeforeEach
    void prepararDatosBase() {
        Long rolId = jdbc.queryForObject("SELECT id FROM rol WHERE nombre = 'COLABORADOR'", Long.class);
        usuarioId = jdbc.queryForObject(
                "INSERT INTO usuario (nombres, apellidos, tipo_documento, numero_documento, correo, rol_id) "
                        + "VALUES ('Prueba', 'Restricciones', 'DNI', '99999991', 'restricciones@prueba.local', ?) RETURNING id",
                Long.class, rolId);
        proyectoId = jdbc.queryForObject(
                "INSERT INTO proyecto (nombre, cliente, fecha_inicio) VALUES ('Proyecto', 'Cliente', '2026-01-01') RETURNING id",
                Long.class);
        ubicacionId = jdbc.queryForObject(
                "INSERT INTO ubicacion (proyecto_id, nombre, geom) "
                        + "VALUES (?, 'Sede', ST_GeogFromText('POINT(-77.04 -12.04)')) RETURNING id",
                Long.class, proyectoId);
        horarioId = jdbc.queryForObject(
                "INSERT INTO horario (nombre, hora_inicio, hora_fin) VALUES ('Jornada', '08:00', '17:00') RETURNING id",
                Long.class);
    }

    @Test
    void rechazaAsignacionesSolapadasDelMismoColaborador() {
        insertarAsignacion("2026-02-01", "2026-02-28");

        assertThatThrownBy(() -> insertarAsignacion("2026-02-28", "2026-03-10"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_asignacion_sin_solapamiento");
    }

    @Test
    void permiteAsignacionesContiguasYUnaSinFechaDeFin() {
        insertarAsignacion("2026-02-01", "2026-02-28");

        assertThatCode(() -> insertarAsignacion("2026-03-01", null)).doesNotThrowAnyException();
    }

    @Test
    void rechazaAsignacionAbiertaQueChocaConUnaPosterior() {
        insertarAsignacion("2026-02-01", null);

        assertThatThrownBy(() -> insertarAsignacion("2026-06-01", "2026-06-30"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rechazaHorarioConFinAnteriorAlInicio() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO horario (nombre, hora_inicio, hora_fin) VALUES ('Invertido', '17:00', '08:00')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_horario_jornada");
    }

    @Test
    void rechazaRefrigerioIncompleto() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO horario (nombre, hora_inicio, hora_fin, hora_inicio_refrigerio) "
                        + "VALUES ('Refrigerio', '08:00', '17:00', '13:00')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_horario_refrigerio");
    }

    @Test
    void rechazaUbicacionConRadioNoPositivo() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO ubicacion (proyecto_id, nombre, geom, radio_tolerancia_metros) "
                        + "VALUES (?, 'Sin radio', ST_GeogFromText('POINT(-77 -12)'), 0)", proyectoId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ubicacion_radio_positivo");
    }

    @Test
    void rechazaIncidenciaConEstadoFueraDelDominio() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO incidencia (usuario_id, tipo, estado, fecha_evento, descripcion, creado_por_id) "
                        + "VALUES (?, 'TARDANZA', 'INVENTADO', ?, 'Descripción', ?)",
                usuarioId, LocalDate.of(2026, 2, 1), usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_incidencia_estado");
    }

    @Test
    void rechazaIncidenciaRevisadaSinFechaDeRevision() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO incidencia (usuario_id, tipo, fecha_evento, descripcion, creado_por_id, revisado_por_id) "
                        + "VALUES (?, 'TARDANZA', ?, 'Descripción', ?, ?)",
                usuarioId, LocalDate.of(2026, 2, 1), usuarioId, usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_incidencia_revision_completa");
    }

    private void insertarAsignacion(String fechaInicio, String fechaFin) {
        jdbc.update(
                "INSERT INTO asignacion (usuario_id, proyecto_id, ubicacion_id, horario_id, fecha_inicio, fecha_fin) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                usuarioId, proyectoId, ubicacionId, horarioId,
                LocalDate.parse(fechaInicio), fechaFin == null ? null : LocalDate.parse(fechaFin));
    }
}
