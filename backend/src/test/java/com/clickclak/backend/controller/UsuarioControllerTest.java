package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

/** HU04 de punta a punta: alta, edición, activar/desactivar, listado, roles y bitácora. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private BitacoraAuditoriaRepository bitacoraRepository;
    @Autowired private JwtService jwtService;

    @Test
    void flujoCompleto_crearEditarActivarDesactivar_quedaEnBitacora() throws Exception {
        Usuario admin = crearUsuarioConPassword("admin.hu04.1@example.com", Rol.RRHH_ADMIN);
        String token = "Bearer " + jwtService.generarToken(admin);

        String cuerpoCreacion = """
            {"nombres":"Carlos","apellidos":"Mendoza","tipoDocumento":"DNI","numeroDocumento":"88888801",
             "correo":"tecnico.hu04.1@example.com","rol":"COLABORADOR"}
            """;
        String respuestaCreacion = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCreacion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();
        Long usuarioId = objectMapper.readTree(respuestaCreacion).get("id").asLong();

        mockMvc.perform(put("/api/usuarios/" + usuarioId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombres":"Carlos Alberto","apellidos":"Mendoza","tipoDocumento":"DNI",
                             "numeroDocumento":"88888801","correo":"tecnico.hu04.1@example.com","rol":"COLABORADOR"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Carlos Alberto"));

        mockMvc.perform(post("/api/usuarios/" + usuarioId + "/desactivar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(post("/api/usuarios/" + usuarioId + "/activar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        var entradas = bitacoraRepository.findByEntidadAndEntidadId("usuario", usuarioId);
        assertThat(entradas).hasSize(4); // creación, edición, desactivar, activar
    }

    @Test
    void crearColaboradorConPassword_devuelve400() throws Exception {
        Usuario admin = crearUsuarioConPassword("admin.hu04.2@example.com", Rol.RRHH_ADMIN);
        String token = "Bearer " + jwtService.generarToken(admin);

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombres":"Carlos","apellidos":"Mendoza","tipoDocumento":"DNI","numeroDocumento":"88888802",
                             "correo":"tecnico.hu04.2@example.com","rol":"COLABORADOR","password":"noDeberia123"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearConCorreoDuplicado_devuelve409() throws Exception {
        Usuario admin = crearUsuarioConPassword("admin.hu04.3@example.com", Rol.RRHH_ADMIN);
        String token = "Bearer " + jwtService.generarToken(admin);
        crearUsuarioConPassword("duplicado.hu04@example.com", Rol.SUPERVISOR);

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombres":"Otro","apellidos":"Usuario","tipoDocumento":"DNI","numeroDocumento":"88888803",
                             "correo":"duplicado.hu04@example.com","rol":"COLABORADOR"}
                            """))
                .andExpect(status().isConflict());
    }

    @Test
    void crearComoSupervisor_devuelve403() throws Exception {
        Usuario supervisor = crearUsuarioConPassword("supervisor.hu04.1@example.com", Rol.SUPERVISOR);
        String token = "Bearer " + jwtService.generarToken(supervisor);

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombres":"Carlos","apellidos":"Mendoza","tipoDocumento":"DNI","numeroDocumento":"88888804",
                             "correo":"tecnico.hu04.3@example.com","rol":"COLABORADOR"}
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarComoSupervisor_estaPermitido_peroComoColaboradorNo() throws Exception {
        Usuario supervisor = crearUsuarioConPassword("supervisor.hu04.2@example.com", Rol.SUPERVISOR);
        String tokenSupervisor = "Bearer " + jwtService.generarToken(supervisor);

        mockMvc.perform(get("/api/usuarios").header("Authorization", tokenSupervisor))
                .andExpect(status().isOk());

        Usuario tecnico = crearUsuarioSinPassword("tecnico.hu04.listar@example.com", Rol.COLABORADOR);
        String tokenTecnico = "Bearer " + jwtService.generarToken(tecnico);

        mockMvc.perform(get("/api/usuarios").header("Authorization", tokenTecnico))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtenerUsuarioInexistente_devuelve404() throws Exception {
        Usuario admin = crearUsuarioConPassword("admin.hu04.4@example.com", Rol.RRHH_ADMIN);
        String token = "Bearer " + jwtService.generarToken(admin);

        mockMvc.perform(get("/api/usuarios/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    private Usuario crearUsuarioConPassword(String correo, String nombreRol) {
        Rol rol = rolRepository.findByNombre(nombreRol).orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .nombres("Ana").apellidos("Pérez")
                .tipoDocumento("DNI").numeroDocumento(Integer.toString(correo.hashCode()))
                .correo(correo)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvwxyzABCDE")
                .rol(rol)
                .activo(true)
                .build());
    }

    private Usuario crearUsuarioSinPassword(String correo, String nombreRol) {
        Rol rol = rolRepository.findByNombre(nombreRol).orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .nombres("Carlos").apellidos("Mendoza")
                .tipoDocumento("DNI").numeroDocumento(Integer.toString(correo.hashCode()))
                .correo(correo)
                .rol(rol)
                .activo(true)
                .build());
    }
}
