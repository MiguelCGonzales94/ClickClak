package com.clickclak.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Incidencia;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.TipoIncidencia;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;
import com.clickclak.backend.service.IncidenciaService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Recorre el flujo de incidencias por HTTP contra la base real: permisos por rol, propiedad de
 * los datos, transiciones válidas e inválidas, y que cada cambio deje historial y auditoría
 * (incluida la serialización a jsonb de la bitácora, que solo se comprueba con Postgres real).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidenciaControllerTest {

    private static final LocalDate AYER = LocalDate.now(ZoneId.of("America/Lima")).minusDays(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JwtService jwtService;
    @Autowired private IncidenciaService incidenciaService;

    private Usuario colaborador;
    private Usuario otroColaborador;
    private Usuario supervisor;
    private Usuario rrhh;

    @BeforeEach
    void crearUsuarios() {
        colaborador = crearUsuario("incidencias.colaborador@prueba.local", "90000001", Rol.COLABORADOR);
        otroColaborador = crearUsuario("incidencias.otro@prueba.local", "90000002", Rol.COLABORADOR);
        supervisor = crearUsuario("incidencias.supervisor@prueba.local", "90000003", Rol.SUPERVISOR);
        rrhh = crearUsuario("incidencias.rrhh@prueba.local", "90000004", Rol.RRHH_ADMIN);
    }

    @Test
    void flujoCompleto_dejaHistorialYAuditoriaDeCadaPaso() throws Exception {
        long id = registrarComoColaborador(colaborador, TipoIncidencia.AUSENCIA);

        mockMvc.perform(get("/api/incidencias").param("estado", "REGISTRADA")
                        .header("Authorization", bearer(supervisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].estado").value("REGISTRADA"));

        accion(supervisor, id, "iniciar-revision", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
        accion(supervisor, id, "aprobar", Map.of("comentario", "Sustento válido")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.revisadoPorId").value(supervisor.getId()))
                .andExpect(jsonPath("$.comentarioRevision").value("Sustento válido"));
        accion(rrhh, id, "cerrar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"));

        // El propio colaborador ve la traza completa de su incidencia.
        mockMvc.perform(get("/api/incidencias/" + id).header("Authorization", bearer(colaborador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial.length()").value(4))
                .andExpect(jsonPath("$.historial[0].estadoAnterior").doesNotExist())
                .andExpect(jsonPath("$.historial[0].estadoNuevo").value("REGISTRADA"))
                .andExpect(jsonPath("$.historial[3].estadoAnterior").value("APROBADA"))
                .andExpect(jsonPath("$.historial[3].estadoNuevo").value("CERRADA"))
                .andExpect(jsonPath("$.historial[3].usuarioId").value(rrhh.getId()));

        // Auditoría: una creación y tres modificaciones, con el JSON realmente en jsonb.
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM bitacora_auditoria WHERE entidad = 'incidencia' AND entidad_id = ?",
                Integer.class, id);
        assertThat(filas).isEqualTo(4);
        Map<String, Object> ultima = jdbc.queryForMap(
                "SELECT usuario_id, accion, valores_anteriores ->> 'estado' AS antes, valores_nuevos ->> 'estado' AS despues "
                        + "FROM bitacora_auditoria WHERE entidad = 'incidencia' AND entidad_id = ? ORDER BY id DESC LIMIT 1",
                id);
        assertThat(ultima).containsEntry("accion", "MODIFICACION")
                .containsEntry("antes", "APROBADA").containsEntry("despues", "CERRADA");
        assertThat(((Number) ultima.get("usuario_id")).longValue()).isEqualTo(rrhh.getId());
    }

    @Test
    void rechazadaConMotivoYLuegoCerrada() throws Exception {
        long id = registrarComoColaborador(colaborador, TipoIncidencia.JUSTIFICACION);

        accion(supervisor, id, "iniciar-revision", null).andExpect(status().isOk());
        accion(supervisor, id, "rechazar", Map.of("comentario", "Falta documento")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.comentarioRevision").value("Falta documento"));
        accion(supervisor, id, "cerrar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"));
    }

    @Test
    void rechazarSinMotivoDevuelve400() throws Exception {
        long id = registrarComoColaborador(colaborador, TipoIncidencia.AUSENCIA);
        accion(supervisor, id, "iniciar-revision", null).andExpect(status().isOk());

        accion(supervisor, id, "rechazar", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void saltarseElFlujoDevuelve409() throws Exception {
        long id = registrarComoColaborador(colaborador, TipoIncidencia.AUSENCIA);

        accion(supervisor, id, "aprobar", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
        accion(supervisor, id, "cerrar", null).andExpect(status().isConflict());
    }

    @Test
    void colaboradorNoPuedeCambiarEstados() throws Exception {
        long id = registrarComoColaborador(colaborador, TipoIncidencia.AUSENCIA);

        accion(colaborador, id, "iniciar-revision", null).andExpect(status().isForbidden());
        accion(colaborador, id, "aprobar", null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/incidencias").header("Authorization", bearer(colaborador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void colaboradorSoloVeLoSuyo() throws Exception {
        long propia = registrarComoColaborador(colaborador, TipoIncidencia.AUSENCIA);
        long ajena = registrarComoColaborador(otroColaborador, TipoIncidencia.AUSENCIA);

        mockMvc.perform(get("/api/incidencias/" + ajena).header("Authorization", bearer(colaborador)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/incidencias/mias").header("Authorization", bearer(colaborador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(propia));
    }

    @Test
    void colaboradorNoRegistraANombreDeOtro() throws Exception {
        registrar(colaborador, Map.of("usuarioId", otroColaborador.getId(), "tipo", "AUSENCIA",
                "fechaEvento", AYER.toString(), "descripcion", "No vino"))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorRegistraANombreDeUnColaborador() throws Exception {
        registrar(supervisor, Map.of("usuarioId", colaborador.getId(), "tipo", "OLVIDO_REGISTRO",
                "fechaEvento", AYER.toString(), "descripcion", "Olvidó marcar la salida"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(colaborador.getId()))
                .andExpect(jsonPath("$.creadoPorId").value(supervisor.getId()));
    }

    @Test
    void nadieRevisaLoQueRegistroANombreDeOtro() throws Exception {
        long id = registrarComoSupervisorParaColaborador();

        accion(supervisor, id, "iniciar-revision", null).andExpect(status().isForbidden());
        accion(rrhh, id, "iniciar-revision", null).andExpect(status().isOk());
    }

    @Test
    void duplicadaMientrasEstaAbiertaDevuelve409() throws Exception {
        registrarComoColaborador(colaborador, TipoIncidencia.AUSENCIA);

        registrar(colaborador, Map.of("tipo", "AUSENCIA", "fechaEvento", AYER.toString(), "descripcion", "Otra vez"))
                .andExpect(status().isConflict());
    }

    @Test
    void descripcionVaciaDevuelve400() throws Exception {
        registrar(colaborador, Map.of("tipo", "AUSENCIA", "fechaEvento", AYER.toString(), "descripcion", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void incidenciaInexistenteDevuelve404() throws Exception {
        accion(supervisor, 987654321L, "iniciar-revision", null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/incidencias/987654321").header("Authorization", bearer(colaborador)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/incidencias/mias")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/incidencias").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void incidenciaAutomatica_nacerConHistorialYAuditoriaDelSistema() {
        Incidencia automatica = incidenciaService.registrarAutomatica(
                colaborador, null, TipoIncidencia.TARDANZA, AYER, "Generada por el motor de validación");

        Integer historial = jdbc.queryForObject(
                "SELECT count(*) FROM incidencia_historial WHERE incidencia_id = ? AND estado_anterior IS NULL AND estado_nuevo = 'REGISTRADA'",
                Integer.class, automatica.getId());
        assertThat(historial).isEqualTo(1);

        Map<String, Object> auditoria = jdbc.queryForMap(
                "SELECT usuario_id, accion, valores_nuevos ->> 'tipo' AS tipo FROM bitacora_auditoria "
                        + "WHERE entidad = 'incidencia' AND entidad_id = ?", automatica.getId());
        assertThat(auditoria.get("usuario_id")).isNull(); // la hizo el sistema, no una persona
        assertThat(auditoria).containsEntry("accion", "CREACION").containsEntry("tipo", "TARDANZA");
    }

    // --- apoyo ------------------------------------------------------------------------------

    private long registrarComoColaborador(Usuario actor, TipoIncidencia tipo) throws Exception {
        String cuerpo = registrar(actor, Map.of("tipo", tipo.name(), "fechaEvento", AYER.toString(),
                "descripcion", "Descripción del caso"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private long registrarComoSupervisorParaColaborador() throws Exception {
        String cuerpo = registrar(supervisor, Map.of("usuarioId", colaborador.getId(), "tipo", "OLVIDO_REGISTRO",
                "fechaEvento", AYER.toString(), "descripcion", "Registrada por el supervisor"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private ResultActions registrar(Usuario actor, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/incidencias")
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions accion(Usuario actor, long id, String accion, Map<String, Object> cuerpo) throws Exception {
        var solicitud = post("/api/incidencias/" + id + "/" + accion).header("Authorization", bearer(actor));
        if (cuerpo != null) {
            solicitud.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo));
        }
        return mockMvc.perform(solicitud);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Usuario crearUsuario(String correo, String documento, String rol) {
        return usuarioRepository.save(Usuario.builder()
                .nombres("Prueba").apellidos(rol).tipoDocumento("DNI").numeroDocumento(documento)
                .correo(correo).rol(rolRepository.findByNombre(rol).orElseThrow()).activo(true).build());
    }
}
