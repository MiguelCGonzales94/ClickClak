package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Cubre HU06-HU09 de punta a punta contra la base real: un supervisor da de alta proyecto,
 * sede y turno, asigna un técnico, y el técnico consulta su propia agenda — verificando
 * además que un colaborador no puede dar de alta configuración y que las fechas
 * superpuestas se rechazan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfiguracionOperativaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JwtService jwtService;

    @Test
    void supervisorConfiguraYAsignaTecnico_tecnicoVeSuPropiaAgenda() throws Exception {
        Usuario supervisor = crearUsuario("supervisor.ops1@example.com", Rol.SUPERVISOR);
        Usuario tecnico = crearUsuario("tecnico.ops1@example.com", Rol.COLABORADOR);
        String tokenSupervisor = "Bearer " + jwtService.generarToken(supervisor);
        String tokenTecnico = "Bearer " + jwtService.generarToken(tecnico);

        Long proyectoId = crearProyecto(tokenSupervisor, "Proyecto Ops 1");
        Long ubicacionId = crearUbicacion(tokenSupervisor, proyectoId);
        Long horarioId = crearHorario(tokenSupervisor, "Turno Ops 1");

        mockMvc.perform(post("/api/asignaciones")
                        .header("Authorization", tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"usuarioId":%d,"proyectoId":%d,"ubicacionId":%d,"horarioId":%d,"fechaInicio":"2026-03-01"}
                            """.formatted(tecnico.getId(), proyectoId, ubicacionId, horarioId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("VIGENTE"));

        mockMvc.perform(get("/api/asignaciones/mias").header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombreProyecto").value("Proyecto Ops 1"));
    }

    @Test
    void colaboradorIntentaCrearProyecto_devuelve403() throws Exception {
        Usuario tecnico = crearUsuario("tecnico.ops2@example.com", Rol.COLABORADOR);
        String token = "Bearer " + jwtService.generarToken(tecnico);

        mockMvc.perform(post("/api/proyectos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombre":"Proyecto no autorizado","cliente":"Cliente X","fechaInicio":"2026-01-01"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void asignacionConFechasSuperpuestas_devuelve409() throws Exception {
        Usuario supervisor = crearUsuario("supervisor.ops3@example.com", Rol.SUPERVISOR);
        Usuario tecnico = crearUsuario("tecnico.ops3@example.com", Rol.COLABORADOR);
        String tokenSupervisor = "Bearer " + jwtService.generarToken(supervisor);

        Long proyectoId = crearProyecto(tokenSupervisor, "Proyecto Ops 3");
        Long ubicacionId = crearUbicacion(tokenSupervisor, proyectoId);
        Long horarioId = crearHorario(tokenSupervisor, "Turno Ops 3");

        String cuerpoAsignacion = """
            {"usuarioId":%d,"proyectoId":%d,"ubicacionId":%d,"horarioId":%d,"fechaInicio":"2026-05-01","fechaFin":"2026-05-31"}
            """.formatted(tecnico.getId(), proyectoId, ubicacionId, horarioId);

        mockMvc.perform(post("/api/asignaciones")
                        .header("Authorization", tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAsignacion))
                .andExpect(status().isCreated());

        String cuerpoSuperpuesto = """
            {"usuarioId":%d,"proyectoId":%d,"ubicacionId":%d,"horarioId":%d,"fechaInicio":"2026-05-15"}
            """.formatted(tecnico.getId(), proyectoId, ubicacionId, horarioId);

        mockMvc.perform(post("/api/asignaciones")
                        .header("Authorization", tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoSuperpuesto))
                .andExpect(status().isConflict());
    }

    private Long crearProyecto(String tokenSupervisor, String nombre) throws Exception {
        String respuesta = mockMvc.perform(post("/api/proyectos")
                        .header("Authorization", tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombre":"%s","cliente":"Cliente Demo","fechaInicio":"2026-01-01"}
                            """.formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return campoLong(respuesta, "id");
    }

    private Long crearUbicacion(String tokenSupervisor, Long proyectoId) throws Exception {
        String respuesta = mockMvc.perform(post("/api/ubicaciones")
                        .header("Authorization", tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"proyectoId":%d,"nombre":"Sede Demo","latitud":-12.0464,"longitud":-77.0428,"radioToleranciaMetros":150}
                            """.formatted(proyectoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return campoLong(respuesta, "id");
    }

    private Long crearHorario(String tokenSupervisor, String nombre) throws Exception {
        String respuesta = mockMvc.perform(post("/api/horarios")
                        .header("Authorization", tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombre":"%s","horaInicio":"08:00:00","horaFin":"17:00:00","toleranciaMinutos":10}
                            """.formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return campoLong(respuesta, "id");
    }

    private Long campoLong(String json, String campo) throws Exception {
        JsonNode nodo = objectMapper.readTree(json);
        return nodo.get(campo).asLong();
    }

    private Usuario crearUsuario(String correo, String nombreRol) {
        Rol rol = rolRepository.findByNombre(nombreRol).orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .nombres("Ana").apellidos("Pérez")
                .tipoDocumento("DNI").numeroDocumento(Integer.toString(correo.hashCode()))
                .correo(correo)
                .rol(rol)
                .activo(true)
                .build());
    }
}
