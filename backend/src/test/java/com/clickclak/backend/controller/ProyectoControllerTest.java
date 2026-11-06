package com.clickclak.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

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

import com.clickclak.backend.model.Asignacion;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.HorarioRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

/**
 * HU06 contra la base real: alta, corrección, baja y eliminación de servicios, con su bitácora, y las
 * reglas que impiden desactivar o borrar lo que aún se usa o dejó historial.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProyectoControllerTest {

    private static final GeometryFactory FABRICA = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private BitacoraAuditoriaRepository bitacoraRepository;
    @Autowired private JwtService jwtService;

    @Test
    void registrarYEditar_dejanBitacora_yValidanLasFechas() throws Exception {
        Usuario admin = crearUsuario("prj.editar", Rol.RRHH_ADMIN, "81");
        Long id = registrar(admin, "Servicio uno", "Cliente uno", "2026-10-01", null);

        mockMvc.perform(put("/api/proyectos/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Servicio dos", "Cliente dos", "2026-10-02", "2026-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Servicio dos"))
                .andExpect(jsonPath("$.cliente").value("Cliente dos"))
                .andExpect(jsonPath("$.fechaFin").value("2026-12-31"));

        mockMvc.perform(put("/api/proyectos/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("X", "Y", "2026-10-02", "2026-09-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("fecha de fin")));
        mockMvc.perform(put("/api/proyectos/999999999").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("X", "Y", "2026-10-02", null)))
                .andExpect(status().isNotFound());

        var entradas = bitacoraRepository.findByEntidadAndEntidadId("proyecto", id);
        assertThat(entradas).extracting(e -> e.getAccion().name()).containsExactlyInAnyOrder("CREACION", "MODIFICACION");
        assertThat(entradas).allSatisfy(e -> assertThat(e.getUsuario().getId()).isEqualTo(admin.getId()));
    }

    @Test
    void desactivar_rechazaElServicioConAsignacionesEnCurso_yPermiteElLibre() throws Exception {
        Usuario admin = crearUsuario("prj.baja", Rol.RRHH_ADMIN, "82");
        Usuario tecnico = crearUsuario("prj.baja.tec", Rol.COLABORADOR, "83");
        Proyecto ocupado = proyectoRepository.save(Proyecto.builder().nombre("Ocupado").cliente("C")
                .fechaInicio(LocalDate.of(2020, 1, 1)).build());
        Proyecto libre = proyectoRepository.save(Proyecto.builder().nombre("Libre").cliente("C")
                .fechaInicio(LocalDate.of(2020, 1, 1)).build());
        asignar(tecnico, ocupado, "Sede ocupada");

        mockMvc.perform(post("/api/proyectos/" + ocupado.getId() + "/desactivar").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("asignaciones")));

        mockMvc.perform(post("/api/proyectos/" + libre.getId() + "/desactivar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(post("/api/proyectos/" + libre.getId() + "/reactivar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void eliminar_soloSiNuncaTuvoSedesNiAsignaciones() throws Exception {
        Usuario admin = crearUsuario("prj.elim", Rol.RRHH_ADMIN, "84");
        Long vacio = registrar(admin, "Vacío", "C", "2026-10-01", null);
        Proyecto conSede = proyectoRepository.save(Proyecto.builder().nombre("Con sede").cliente("C")
                .fechaInicio(LocalDate.of(2020, 1, 1)).build());
        ubicacionRepository.save(Ubicacion.builder().proyecto(conSede).nombre("Sede")
                .geom(FABRICA.createPoint(new Coordinate(-77.0, -12.0))).build());

        mockMvc.perform(delete("/api/proyectos/" + conSede.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("desactívelo")));
        assertThat(proyectoRepository.existsById(conSede.getId())).isTrue();

        mockMvc.perform(delete("/api/proyectos/" + vacio).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(proyectoRepository.existsById(vacio)).isFalse();
        assertThat(bitacoraRepository.findByEntidadAndEntidadId("proyecto", vacio))
                .extracting(e -> e.getAccion().name()).contains("ELIMINACION");
    }

    @Test
    void noSeAsignaANadieAUnaSedeOServicioDesactivados() throws Exception {
        Usuario admin = crearUsuario("prj.asig", Rol.RRHH_ADMIN, "85");
        Usuario tecnico = crearUsuario("prj.asig.tec", Rol.COLABORADOR, "86");
        Proyecto proyecto = proyectoRepository.save(Proyecto.builder().nombre("Servicio asig").cliente("C")
                .fechaInicio(LocalDate.of(2020, 1, 1)).build());
        Ubicacion sede = ubicacionRepository.save(Ubicacion.builder().proyecto(proyecto).nombre("Sede asig")
                .geom(FABRICA.createPoint(new Coordinate(-77.0, -12.0))).build());
        Horario turno = horarioRepository.save(Horario.builder().nombre("Turno asig")
                .horaInicio(LocalTime.MIDNIGHT).horaFin(LocalTime.of(23, 59)).toleranciaMinutos(30).build());
        String cuerpo = "{\"usuarioId\":" + tecnico.getId() + ",\"proyectoId\":" + proyecto.getId()
                + ",\"ubicacionId\":" + sede.getId() + ",\"horarioId\":" + turno.getId() + ",\"fechaInicio\":\"2026-10-01\"}";

        sede.setActivo(false);
        ubicacionRepository.save(sede);
        mockMvc.perform(post("/api/asignaciones").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("sede")));

        sede.setActivo(true);
        ubicacionRepository.save(sede);
        proyecto.setActivo(false);
        proyectoRepository.save(proyecto);
        mockMvc.perform(post("/api/asignaciones").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("servicio")));
    }

    @Test
    void unColaboradorNoPuedeGestionarServicios() throws Exception {
        Usuario admin = crearUsuario("prj.perm", Rol.RRHH_ADMIN, "87");
        Usuario tecnico = crearUsuario("prj.perm.tec", Rol.COLABORADOR, "88");
        Long id = registrar(admin, "Servicio", "C", "2026-10-01", null);

        mockMvc.perform(put("/api/proyectos/" + id).header("Authorization", bearer(tecnico))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("X", "Y", "2026-10-01", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/proyectos/" + id + "/desactivar").header("Authorization", bearer(tecnico)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/proyectos/" + id).header("Authorization", bearer(tecnico)))
                .andExpect(status().isForbidden());
    }

    private void asignar(Usuario tecnico, Proyecto proyecto, String nombreSede) {
        Ubicacion sede = ubicacionRepository.save(Ubicacion.builder().proyecto(proyecto).nombre(nombreSede)
                .geom(FABRICA.createPoint(new Coordinate(-77.0, -12.0))).build());
        Horario turno = horarioRepository.save(Horario.builder().nombre("Turno " + nombreSede)
                .horaInicio(LocalTime.MIDNIGHT).horaFin(LocalTime.of(23, 59)).toleranciaMinutos(30).build());
        asignacionRepository.save(Asignacion.builder().usuario(tecnico).proyecto(proyecto).ubicacion(sede)
                .horario(turno).fechaInicio(LocalDate.of(2020, 1, 1)).build());
    }

    private Long registrar(Usuario actor, String nombre, String cliente, String inicio, String fin) throws Exception {
        String respuesta = mockMvc.perform(post("/api/proyectos").header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombre, cliente, inicio, fin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    private String cuerpo(String nombre, String cliente, String inicio, String fin) {
        return "{\"nombre\":\"" + nombre + "\",\"cliente\":\"" + cliente + "\",\"fechaInicio\":\"" + inicio + "\""
                + (fin != null ? ",\"fechaFin\":\"" + fin + "\"" : "") + "}";
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Usuario crearUsuario(String sufijo, String rol, String prefijoDocumento) {
        int hash = Math.abs(sufijo.hashCode()) % 9_000_000;
        return usuarioRepository.save(Usuario.builder()
                .nombres("Persona").apellidos(sufijo).tipoDocumento("DNI")
                .numeroDocumento(prefijoDocumento + String.format("%06d", hash))
                .correo(sufijo + "@example.com")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvwxyzABCDE")
                .rol(rolRepository.findByNombre(rol).orElseThrow()).activo(true).build());
    }
}
