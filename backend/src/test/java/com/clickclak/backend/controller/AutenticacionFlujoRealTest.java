package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;

/**
 * Deliberadamente SIN {@code @Transactional}: esa anotación en un test mantiene la sesión de
 * Hibernate abierta durante todo el método, lo que esconde cualquier {@code LazyInitializationException}
 * que solo ocurre cuando cada request corre en su propia transacción — como en producción real
 * (open-in-view: false). Este test reprodujo exactamente ese bug en AutenticacionService antes
 * de agregarle @Transactional a sus métodos; se limpia manualmente porque no hay rollback automático.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionFlujoRealTest {

    private static final String CORREO = "supervisor.flujo.real@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @AfterEach
    void limpiar() {
        usuarioRepository.findByCorreo(CORREO).ifPresent(usuarioRepository::delete);
    }

    @Test
    void loginYPerfil_fueraDeUnaTransaccionDeTest_noLanzaLazyInitializationException() throws Exception {
        Rol rolSupervisor = rolRepository.findByNombre(Rol.SUPERVISOR).orElseThrow();
        usuarioRepository.save(Usuario.builder()
                .nombres("Flujo").apellidos("Real")
                .tipoDocumento("DNI").numeroDocumento(Integer.toString(CORREO.hashCode()))
                .correo(CORREO)
                .passwordHash(passwordEncoder.encode("claveSegura123"))
                .rol(rolSupervisor)
                .activo(true)
                .build());

        String cuerpoLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"password\":\"claveSegura123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value(Rol.SUPERVISOR))
                .andReturn().getResponse().getContentAsString();

        String token = cuerpoLogin.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value(Rol.SUPERVISOR));
    }
}
