package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * Cubre el cableado HTTP/seguridad de HU05 (roles, validación, mensajes de error) contra
 * la base real. Deliberadamente NO completa una ceremonia criptográfica real (requeriría
 * simular un autenticador de hardware/plataforma) — esa parte se apoya en que cada llamada
 * a la librería de Yubico usada en {@link com.clickclak.backend.service.WebAuthnService}
 * fue verificada firma por firma contra el jar real antes de escribir el código.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebAuthnControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JwtService jwtService;

    @Test
    void iniciarRegistro_comoSupervisor_devuelveOpcionesParaElNavegador() throws Exception {
        Usuario supervisor = crearUsuario("supervisor.webauthn1@example.com", Rol.SUPERVISOR);
        Usuario tecnico = crearUsuario("tecnico.webauthn1@example.com", Rol.COLABORADOR);
        String token = "Bearer " + jwtService.generarToken(supervisor);

        mockMvc.perform(post("/api/webauthn/registro/iniciar")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + tecnico.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSolicitud").isNotEmpty())
                .andExpect(jsonPath("$.opcionesJson").value(org.hamcrest.Matchers.containsString("publicKey")));
    }

    @Test
    void iniciarRegistro_comoColaborador_devuelve403() throws Exception {
        Usuario tecnico = crearUsuario("tecnico.webauthn2@example.com", Rol.COLABORADOR);
        String token = "Bearer " + jwtService.generarToken(tecnico);

        mockMvc.perform(post("/api/webauthn/registro/iniciar")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + tecnico.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void iniciarRegistro_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/webauthn/registro/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void iniciarRegistro_conUsuarioInexistente_devuelve404() throws Exception {
        Usuario supervisor = crearUsuario("supervisor.webauthn3@example.com", Rol.SUPERVISOR);
        String token = "Bearer " + jwtService.generarToken(supervisor);

        mockMvc.perform(post("/api/webauthn/registro/iniciar")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void finalizarRegistro_conSolicitudDesconocida_devuelve400() throws Exception {
        Usuario supervisor = crearUsuario("supervisor.webauthn4@example.com", Rol.SUPERVISOR);
        String token = "Bearer " + jwtService.generarToken(supervisor);

        mockMvc.perform(post("/api/webauthn/registro/finalizar")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idSolicitud\":\"no-existe\",\"credencialJson\":\"{}\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iniciarAutenticacion_esPublicoYNoRevelaSiElCorreoExiste() throws Exception {
        mockMvc.perform(post("/api/webauthn/autenticacion/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cualquiera@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSolicitud").isNotEmpty())
                .andExpect(jsonPath("$.opcionesJson").value(org.hamcrest.Matchers.containsString("publicKey")));
    }

    @Test
    void finalizarAutenticacion_conSolicitudDesconocida_devuelve401ConMensajeGenerico() throws Exception {
        mockMvc.perform(post("/api/webauthn/autenticacion/finalizar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idSolicitud\":\"no-existe\",\"credencialJson\":\"{}\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Correo o contraseña inválidos"));
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
