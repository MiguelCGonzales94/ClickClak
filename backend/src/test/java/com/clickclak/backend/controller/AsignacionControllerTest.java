package com.clickclak.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.TipoEvento;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.HorarioRepository;
import com.clickclak.backend.repository.MarcacionRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HU08 contra la base real: un técnico con varias sedes a la vez, editar, mover a otra sede y
 * quitar; la regla de la misma sede; los permisos; la bitácora; y que una marcación se valide
 * contra la sede más cercana de las vigentes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AsignacionControllerTest {

    private static final GeometryFactory FABRICA = new GeometryFactory(new PrecisionModel(), 4326);
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private DispositivoRepository dispositivoRepository;
    @Autowired private MarcacionRepository marcacionRepository;
    @Autowired private BitacoraAuditoriaRepository bitacoraRepository;
    @Autowired private JwtService jwtService;

    private record Datos(Usuario admin, Usuario tecnico, Proyecto proyecto, Ubicacion sedeA, Ubicacion sedeB,
                          Horario turnoManana, Horario turnoTarde) {
    }

    @Test
    void unTecnicoPuedeTenerVariasSedesALaVezPeroNoLaMismaDosVeces() throws Exception {
        Datos d = crearDatos("asig.multi");

        Long enA = crear(d, d.sedeA(), d.turnoManana(), "2020-03-01", null, 201);
        crear(d, d.sedeB(), d.turnoTarde(), "2020-03-01", null, 201);
        crear(d, d.sedeA(), d.turnoTarde(), "2020-04-01", "2020-04-30", 409);

        mockMvc.perform(get("/api/asignaciones").param("usuarioId", d.tecnico().getId().toString())
                        .header("Authorization", bearer(d.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombreUbicacion").value("Sede A multi"))
                .andExpect(jsonPath("$[1].nombreUbicacion").value("Sede B multi"));
        assertThat(enA).isNotNull();
    }

    @Test
    void laSedeDebePertenecerAlServicio() throws Exception {
        Datos d = crearDatos("asig.servicio");
        Proyecto otro = proyectoRepository.save(Proyecto.builder()
                .nombre("Otro servicio").cliente("Cliente").fechaInicio(LocalDate.of(2020, 1, 1)).build());

        mockMvc.perform(post("/api/asignaciones").header("Authorization", bearer(d.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(d.tecnico(), otro.getId(), d.sedeA().getId(), d.turnoManana().getId(), "2020-03-01", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("no pertenece al servicio")));
    }

    @Test
    void editar_cambiaSedeYTurno_yQuedaEnLaBitacora() throws Exception {
        Datos d = crearDatos("asig.editar");
        Long id = crear(d, d.sedeA(), d.turnoManana(), "2020-03-01", null, 201);

        mockMvc.perform(put("/api/asignaciones/" + id).header("Authorization", bearer(d.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicion(d.proyecto(), d.sedeB(), d.turnoTarde(), "2020-03-05", "2020-09-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUbicacion").value("Sede B editar"))
                .andExpect(jsonPath("$.nombreHorario").value("Turno tarde editar"))
                .andExpect(jsonPath("$.fechaInicio").value("2020-03-05"))
                .andExpect(jsonPath("$.fechaFin").value("2020-09-30"));

        var entradas = bitacoraRepository.findByEntidadAndEntidadId("asignacion", id);
        assertThat(entradas).extracting(e -> e.getAccion().name()).containsExactlyInAnyOrder("CREACION", "MODIFICACION");
        assertThat(entradas).allSatisfy(e -> assertThat(e.getUsuario().getId()).isEqualTo(d.admin().getId()));
    }

    @Test
    void editar_aUnaSedeQueYaTieneEnEsasFechas_respondeConflicto() throws Exception {
        Datos d = crearDatos("asig.edconf");
        Long enA = crear(d, d.sedeA(), d.turnoManana(), "2020-03-01", null, 201);
        crear(d, d.sedeB(), d.turnoManana(), "2020-03-01", null, 201);

        mockMvc.perform(put("/api/asignaciones/" + enA).header("Authorization", bearer(d.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicion(d.proyecto(), d.sedeB(), d.turnoManana(), "2020-03-01", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void mover_terminaLaActualYCreaLaNuevaEnLaOtraSede() throws Exception {
        Datos d = crearDatos("asig.mover");
        Long id = crear(d, d.sedeA(), d.turnoManana(), "2020-03-01", null, 201);

        mockMvc.perform(post("/api/asignaciones/" + id + "/mover").header("Authorization", bearer(d.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ubicacionId\":" + d.sedeB().getId() + ",\"fechaCambio\":\"2020-06-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreUbicacion").value("Sede B mover"))
                .andExpect(jsonPath("$.nombreHorario").value("Turno mañana mover"))
                .andExpect(jsonPath("$.fechaInicio").value("2020-06-01"))
                .andExpect(jsonPath("$.estado").value("VIGENTE"));

        mockMvc.perform(get("/api/asignaciones").param("usuarioId", d.tecnico().getId().toString())
                        .header("Authorization", bearer(d.admin())))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombreUbicacion").value("Sede A mover"))
                .andExpect(jsonPath("$[0].fechaFin").value("2020-05-31"))
                .andExpect(jsonPath("$[0].estado").value("FINALIZADA"))
                .andExpect(jsonPath("$[1].nombreUbicacion").value("Sede B mover"));

        // Una fecha de cambio que no es posterior al inicio no tiene sentido: se rechaza sin cambiar nada.
        mockMvc.perform(post("/api/asignaciones/" + id + "/mover").header("Authorization", bearer(d.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ubicacionId\":" + d.sedeB().getId() + ",\"fechaCambio\":\"2020-03-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quitar_dejaDeMostrarlaYUnaSegundaVezNoLaEncuentra() throws Exception {
        Datos d = crearDatos("asig.quitar");
        Long id = crear(d, d.sedeA(), d.turnoManana(), "2020-03-01", null, 201);

        mockMvc.perform(delete("/api/asignaciones/" + id).header("Authorization", bearer(d.admin())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/asignaciones").param("usuarioId", d.tecnico().getId().toString())
                        .header("Authorization", bearer(d.admin())))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/asignaciones/" + id).header("Authorization", bearer(d.admin())))
                .andExpect(status().isNotFound());

        // Y la misma sede se puede volver a asignar: la quitada ya no cuenta para el cruce de fechas.
        crear(d, d.sedeA(), d.turnoManana(), "2020-03-01", null, 201);
    }

    @Test
    void permisos_soloSupervisionModificaYElTecnicoVeSuAgendaConTodasSusSedes() throws Exception {
        Datos d = crearDatos("asig.permisos");
        Long id = crear(d, d.sedeA(), d.turnoManana(), "2020-03-01", null, 201);
        crear(d, d.sedeB(), d.turnoTarde(), "2020-03-01", null, 201);
        String comoTecnico = bearer(d.tecnico());

        mockMvc.perform(post("/api/asignaciones").header("Authorization", comoTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(d.tecnico(), d.proyecto().getId(), d.sedeA().getId(), d.turnoManana().getId(), "2021-01-01", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/asignaciones/" + id).header("Authorization", comoTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicion(d.proyecto(), d.sedeB(), d.turnoTarde(), "2020-03-01", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/asignaciones/" + id).header("Authorization", comoTecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/asignaciones/" + id + "/mover").header("Authorization", comoTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ubicacionId\":" + d.sedeB().getId() + ",\"fechaCambio\":\"2020-06-01\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/asignaciones/mias").header("Authorization", comoTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void laMarcacionSeValidaContraLaSedeMasCercanaDeLasVigentes() throws Exception {
        Datos d = crearDatos("asig.marca");
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        crear(d, d.sedeA(), d.turnoManana(), hoy.minusDays(3).toString(), null, 201);
        crear(d, d.sedeB(), d.turnoManana(), hoy.minusDays(3).toString(), null, 201);
        Dispositivo dispositivo = dispositivoRepository.save(Dispositivo.builder()
                .usuario(d.tecnico()).credentialId("cred-asig-marca").clavePublica("clave")
                .nombreDispositivo("Teléfono").activo(true).build());

        // Marca pegado a la sede B (a unos 6 km de la A): debe quedar con la B y válida.
        UUID uuid = UUID.randomUUID();
        mockMvc.perform(post("/api/marcaciones").header("Authorization", bearer(d.tecnico()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
                            put("uuidCliente", uuid);
                            put("usuarioId", d.tecnico().getId());
                            put("dispositivoId", dispositivo.getId());
                            put("tipoEvento", TipoEvento.ENTRADA);
                            put("horaEvento", Instant.now().minusSeconds(30));
                            put("latitud", -12.1000);
                            put("longitud", -77.0300);
                            put("precisionMetros", BigDecimal.valueOf(10));
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoValidacion").value("VALIDO"));

        var marcacion = marcacionRepository.findByUuidCliente(uuid).orElseThrow();
        assertThat(marcacion.getAsignacion().getUbicacion().getId()).isEqualTo(d.sedeB().getId());
        assertThat(marcacion.getEstadoValidacion()).isEqualTo(EstadoValidacion.VALIDO);
        assertThat(marcacion.getDistanciaMetros()).isLessThan(new BigDecimal("5"));
    }

    // ---- ayudas

    private Long crear(Datos d, Ubicacion sede, Horario turno, String inicio, String fin, int estadoEsperado) throws Exception {
        String cuerpoRespuesta = mockMvc.perform(post("/api/asignaciones").header("Authorization", bearer(d.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(d.tecnico(), d.proyecto().getId(), sede.getId(), turno.getId(), inicio, fin)))
                .andExpect(status().is(estadoEsperado))
                .andReturn().getResponse().getContentAsString();
        return estadoEsperado == 201 ? objectMapper.readTree(cuerpoRespuesta).get("id").asLong() : null;
    }

    private String cuerpo(Usuario tecnico, Long proyectoId, Long ubicacionId, Long horarioId, String inicio, String fin) {
        return "{\"usuarioId\":" + tecnico.getId() + ",\"proyectoId\":" + proyectoId + ",\"ubicacionId\":" + ubicacionId
                + ",\"horarioId\":" + horarioId + ",\"fechaInicio\":\"" + inicio + "\""
                + (fin != null ? ",\"fechaFin\":\"" + fin + "\"" : "") + "}";
    }

    private String cuerpoEdicion(Proyecto proyecto, Ubicacion sede, Horario turno, String inicio, String fin) {
        return "{\"proyectoId\":" + proyecto.getId() + ",\"ubicacionId\":" + sede.getId() + ",\"horarioId\":" + turno.getId()
                + ",\"fechaInicio\":\"" + inicio + "\"" + (fin != null ? ",\"fechaFin\":\"" + fin + "\"" : "") + "}";
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    /** {@code sufijo} distingue los datos de cada prueba (nombres de sedes y turnos, documentos y correos). */
    private Datos crearDatos(String sufijo) {
        String etiqueta = sufijo.substring(sufijo.indexOf('.') + 1);
        int hash = Math.abs(sufijo.hashCode()) % 9_000_000;
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombres("Ada").apellidos("Admin").tipoDocumento("DNI").numeroDocumento("93" + String.format("%06d", hash))
                .correo(sufijo + ".admin@example.com")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvwxyzABCDE")
                .rol(rolRepository.findByNombre(Rol.RRHH_ADMIN).orElseThrow()).activo(true).build());
        Usuario tecnico = usuarioRepository.save(Usuario.builder()
                .nombres("Tomás").apellidos("Técnico").tipoDocumento("DNI").numeroDocumento("94" + String.format("%06d", hash))
                .correo(sufijo + ".tecnico@example.com")
                .rol(rolRepository.findByNombre(Rol.COLABORADOR).orElseThrow()).activo(true).build());
        Proyecto proyecto = proyectoRepository.save(Proyecto.builder()
                .nombre("Servicio " + etiqueta).cliente("Cliente").fechaInicio(LocalDate.of(2020, 1, 1)).build());
        Ubicacion sedeA = ubicacionRepository.save(Ubicacion.builder().proyecto(proyecto).nombre("Sede A " + etiqueta)
                .geom(FABRICA.createPoint(new Coordinate(-77.0428, -12.0464))).radioToleranciaMetros(150).build());
        Ubicacion sedeB = ubicacionRepository.save(Ubicacion.builder().proyecto(proyecto).nombre("Sede B " + etiqueta)
                .geom(FABRICA.createPoint(new Coordinate(-77.0300, -12.1000))).radioToleranciaMetros(150).build());
        Horario manana = horarioRepository.save(Horario.builder().nombre("Turno mañana " + etiqueta)
                .horaInicio(LocalTime.MIDNIGHT).horaFin(LocalTime.of(23, 59)).toleranciaMinutos(24 * 60).build());
        Horario tarde = horarioRepository.save(Horario.builder().nombre("Turno tarde " + etiqueta)
                .horaInicio(LocalTime.MIDNIGHT).horaFin(LocalTime.of(23, 59)).toleranciaMinutos(24 * 60).build());
        return new Datos(admin, tecnico, proyecto, sedeA, sedeB, manana, tarde);
    }
}
