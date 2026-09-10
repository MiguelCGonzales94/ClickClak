package com.clickclak.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;

/**
 * Prueba el flujo completo de autenticación contra la base real: login, emisión de JWT,
 * el filtro que lo valida, y el manejo uniforme de errores 401 (buenas prácticas de
 * seguridad: nunca la página HTML por defecto de Spring Security).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutenticacionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void loginConCredencialesCorrectas_devuelveToken() throws Exception {
        crearSupervisor("supervisor1@example.com", "claveSegura123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"correo":"supervisor1@example.com","password":"claveSegura123"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value(Rol.SUPERVISOR));
    }

    @Test
    void loginConContrasenaIncorrecta_devuelve401ConJson() throws Exception {
        crearSupervisor("supervisor2@example.com", "claveSegura123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"correo":"supervisor2@example.com","password":"incorrecta"}
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(containsString("inválidos")));
    }

    @Test
    void perfilSinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/auth/yo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilConTokenValido_devuelveDatosDelUsuarioAutenticado() throws Exception {
        crearSupervisor("supervisor3@example.com", "claveSegura123");

        String cuerpoLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"correo":"supervisor3@example.com","password":"claveSegura123"}
                            """))
                .andReturn().getResponse().getContentAsString();

        String token = cuerpoLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("supervisor3@example.com"))
                .andExpect(jsonPath("$.rol").value(Rol.SUPERVISOR));
    }

    private void crearSupervisor(String correo, String password) {
        Rol rolSupervisor = rolRepository.findByNombre(Rol.SUPERVISOR).orElseThrow();
        usuarioRepository.save(Usuario.builder()
                .nombres("Ana").apellidos("Pérez")
                .tipoDocumento("DNI").numeroDocumento(correo.hashCode() + "")
                .correo(correo)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rolSupervisor)
                .activo(true)
                .build());
    }
}
