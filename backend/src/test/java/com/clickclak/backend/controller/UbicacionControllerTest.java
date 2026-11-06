package com.clickclak.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
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
 * HU06 contra la base real: la zona que se marca (centro y radio) queda guardada y se lee igual,
 * se puede corregir, cada cambio deja rastro, y una sede con asignaciones en curso no se desactiva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UbicacionControllerTest {

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
    void laZonaMarcadaSeGuardaYSeLeeIgualDesdeLaBase() throws Exception {
        Usuario admin = crearUsuario("ubi.guardar", Rol.RRHH_ADMIN, "91");
        Proyecto proyecto = crearProyecto("guardar");

        Long id = registrar(admin, proyecto, "Almacén Norte", -12.046374, -77.042793, 220);
        entityManager.flush();
        entityManager.clear();

        var guardada = ubicacionRepository.findById(id).orElseThrow();
        assertThat(guardada.getGeom().getY()).isEqualTo(-12.046374);
        assertThat(guardada.getGeom().getX()).isEqualTo(-77.042793);
        assertThat(guardada.getRadioToleranciaMetros()).isEqualTo(220);

        mockMvc.perform(get("/api/ubicaciones").param("proyectoId", proyecto.getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Almacén Norte"))
                .andExpect(jsonPath("$[0].latitud").value(-12.046374))
                .andExpect(jsonPath("$[0].longitud").value(-77.042793))
                .andExpect(jsonPath("$[0].radioToleranciaMetros").value(220));

        // La zona se mide con PostGIS: el propio centro está a 0 m.
        assertThat(ubicacionRepository.calcularDistanciaMetros(id, -12.046374, -77.042793)).isLessThan(1.0);

        var entradas = bitacoraRepository.findByEntidadAndEntidadId("ubicacion", id);
        assertThat(entradas).extracting(e -> e.getAccion().name()).containsExactly("CREACION");
    }

    @Test
    void editar_muevaLaZonaYElRadio_yQuedaEnLaBitacora() throws Exception {
        Usuario admin = crearUsuario("ubi.editar", Rol.RRHH_ADMIN, "92");
        Proyecto proyecto = crearProyecto("editar");
        Long id = registrar(admin, proyecto, "Sede vieja", -12.0464, -77.0428, 150);

        mockMvc.perform(put("/api/ubicaciones/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicion("Sede nueva", -12.1000, -77.0300, 300)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sede nueva"))
                .andExpect(jsonPath("$.latitud").value(-12.1))
                .andExpect(jsonPath("$.longitud").value(-77.03))
                .andExpect(jsonPath("$.radioToleranciaMetros").value(300));
        entityManager.flush();
        entityManager.clear();

        assertThat(ubicacionRepository.calcularDistanciaMetros(id, -12.1000, -77.0300)).isLessThan(1.0);
        var entradas = bitacoraRepository.findByEntidadAndEntidadId("ubicacion", id);
        assertThat(entradas).extracting(e -> e.getAccion().name()).containsExactlyInAnyOrder("CREACION", "MODIFICACION");
        assertThat(entradas).allSatisfy(e -> assertThat(e.getUsuario().getId()).isEqualTo(admin.getId()));
    }

    @Test
    void coordenadasORadioFueraDeRango_seRechazan() throws Exception {
        Usuario admin = crearUsuario("ubi.rango", Rol.RRHH_ADMIN, "93");
        Proyecto proyecto = crearProyecto("rango");

        mockMvc.perform(post("/api/ubicaciones").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(proyecto, "Fuera", 91.0, -77.0, 150)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ubicaciones").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(proyecto, "Sin radio", -12.0, -77.0, 0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/ubicaciones/999999999").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicion("No existe", -12.0, -77.0, 150)))
                .andExpect(status().isNotFound());
    }

    @Test
    void desactivar_rechazaLaSedeConAsignacionesEnCurso_yPermiteLaLibre() throws Exception {
        Usuario admin = crearUsuario("ubi.baja", Rol.RRHH_ADMIN, "94");
        Usuario tecnico = crearUsuario("ubi.baja.tec", Rol.COLABORADOR, "95");
        Proyecto proyecto = crearProyecto("baja");
        Long ocupada = registrar(admin, proyecto, "Sede ocupada", -12.0464, -77.0428, 150);
        Long libre = registrar(admin, proyecto, "Sede libre", -12.1000, -77.0300, 150);

        Horario turno = horarioRepository.save(Horario.builder().nombre("Turno baja")
                .horaInicio(LocalTime.MIDNIGHT).horaFin(LocalTime.of(23, 59)).toleranciaMinutos(30).build());
        asignacionRepository.save(Asignacion.builder().usuario(tecnico).proyecto(proyecto)
                .ubicacion(ubicacionRepository.findById(ocupada).orElseThrow()).horario(turno)
                .fechaInicio(LocalDate.of(2020, 1, 1)).build());

        mockMvc.perform(post("/api/ubicaciones/" + ocupada + "/desactivar").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("asignaciones")));

        mockMvc.perform(post("/api/ubicaciones/" + libre + "/desactivar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(post("/api/ubicaciones/" + libre + "/reactivar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void eliminar_soloSiNingunaAsignacionUsoLaSede() throws Exception {
        Usuario admin = crearUsuario("ubi.elim", Rol.RRHH_ADMIN, "98");
        Usuario tecnico = crearUsuario("ubi.elim.tec", Rol.COLABORADOR, "99");
        Proyecto proyecto = crearProyecto("elim");
        Long conHistorial = registrar(admin, proyecto, "Con historial", -12.0464, -77.0428, 150);
        Long sinHistorial = registrar(admin, proyecto, "Sin historial", -12.1000, -77.0300, 150);

        Horario turno = horarioRepository.save(Horario.builder().nombre("Turno elim")
                .horaInicio(LocalTime.MIDNIGHT).horaFin(LocalTime.of(23, 59)).toleranciaMinutos(30).build());
        // Una asignación ya quitada también cuenta como historial: las marcaciones cuelgan de ella.
        asignacionRepository.save(Asignacion.builder().usuario(tecnico).proyecto(proyecto)
                .ubicacion(ubicacionRepository.findById(conHistorial).orElseThrow()).horario(turno)
                .fechaInicio(LocalDate.of(2020, 1, 1)).fechaFin(LocalDate.of(2020, 2, 1)).activo(false).build());

        mockMvc.perform(delete("/api/ubicaciones/" + conHistorial).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("desactívela")));
        assertThat(ubicacionRepository.existsById(conHistorial)).isTrue();

        mockMvc.perform(delete("/api/ubicaciones/" + sinHistorial).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(ubicacionRepository.existsById(sinHistorial)).isFalse();
        assertThat(bitacoraRepository.findByEntidadAndEntidadId("ubicacion", sinHistorial))
                .extracting(e -> e.getAccion().name()).contains("ELIMINACION");
    }

    @Test
    void noSePuedeAgregarUnaSedeAUnServicioDesactivado() throws Exception {
        Usuario admin = crearUsuario("ubi.svcoff", Rol.RRHH_ADMIN, "90");
        Proyecto proyecto = crearProyecto("svcoff");
        proyecto.setActivo(false);
        proyectoRepository.save(proyecto);

        mockMvc.perform(post("/api/ubicaciones").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(proyecto, "Sede", -12.0, -77.0, 150)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("desactivado")));
    }

    @Test
    void unColaboradorNoPuedeGestionarSedes() throws Exception {
        Usuario admin = crearUsuario("ubi.permisos", Rol.RRHH_ADMIN, "96");
        Usuario tecnico = crearUsuario("ubi.permisos.tec", Rol.COLABORADOR, "97");
        Proyecto proyecto = crearProyecto("permisos");
        Long id = registrar(admin, proyecto, "Sede", -12.0464, -77.0428, 150);

        mockMvc.perform(post("/api/ubicaciones").header("Authorization", bearer(tecnico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(proyecto, "X", -12.0, -77.0, 150)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ubicaciones/" + id).header("Authorization", bearer(tecnico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicion("X", -12.0, -77.0, 150)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ubicaciones/" + id).header("Authorization", bearer(tecnico)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ubicaciones/" + id + "/desactivar").header("Authorization", bearer(tecnico)))
                .andExpect(status().isForbidden());
    }

    private Long registrar(Usuario actor, Proyecto proyecto, String nombre, double lat, double lng, int radio)
            throws Exception {
        String respuesta = mockMvc.perform(post("/api/ubicaciones").header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(proyecto, nombre, lat, lng, radio)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    private String cuerpoAlta(Proyecto proyecto, String nombre, double lat, double lng, int radio) {
        return "{\"proyectoId\":" + proyecto.getId() + ",\"nombre\":\"" + nombre + "\",\"latitud\":" + lat
                + ",\"longitud\":" + lng + ",\"radioToleranciaMetros\":" + radio + "}";
    }

    private String cuerpoEdicion(String nombre, double lat, double lng, int radio) {
        return "{\"nombre\":\"" + nombre + "\",\"latitud\":" + lat + ",\"longitud\":" + lng
                + ",\"radioToleranciaMetros\":" + radio + "}";
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Proyecto crearProyecto(String etiqueta) {
        return proyectoRepository.save(Proyecto.builder()
                .nombre("Servicio " + etiqueta).cliente("Cliente").fechaInicio(LocalDate.of(2020, 1, 1)).build());
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
