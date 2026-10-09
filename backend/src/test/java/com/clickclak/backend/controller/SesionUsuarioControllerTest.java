package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;

/**
 * HU04: el estado del usuario manda sobre el token. El JWT dura 8 h, pero desactivar a alguien,
 * cambiarle el rol o dejarle una clave temporal pendiente tiene efecto en la siguiente petición.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SesionUsuarioControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JwtService jwtService;

    @Test
    void tokenVigenteDeUsuarioActivo_accede() throws Exception {
        Usuario admin = crearUsuario("sesion.activo@example.com", Rol.RRHH_ADMIN);

        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioDesactivadoConTokenVigente_pierdeElAccesoDeInmediato() throws Exception {
        Usuario admin = crearUsuario("sesion.desactivado@example.com", Rol.RRHH_ADMIN);
        String token = bearer(admin);

        admin.setActivo(false);
        usuarioRepository.save(admin);

        mockMvc.perform(get("/api/usuarios").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioEliminadoConTokenVigente_pierdeElAcceso() throws Exception {
        Usuario admin = crearUsuario("sesion.eliminado@example.com", Rol.RRHH_ADMIN);
        String token = bearer(admin);

        usuarioRepository.delete(admin);
        usuarioRepository.flush();

        mockMvc.perform(get("/api/usuarios").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambioDeRol_tieneEfectoAunConElTokenAnterior() throws Exception {
        Usuario usuario = crearUsuario("sesion.rol@example.com", Rol.RRHH_ADMIN);
        String token = bearer(usuario);

        usuario.setRol(rolRepository.findByNombre(Rol.SUPERVISOR).orElseThrow());
        usuarioRepository.save(usuario);

        // El token sigue diciendo RRHH_ADMIN, pero ahora es SUPERVISOR: crear usuarios está vetado.
        mockMvc.perform(post("/api/usuarios/1/activar").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void claveTemporalPendiente_bloqueaTodoMenosElPerfilYElCierreDeSesion() throws Exception {
        Usuario admin = crearUsuario("sesion.pendiente@example.com", Rol.RRHH_ADMIN);
        admin.setDebeCambiarClave(true);
        usuarioRepository.save(admin);
        String token = bearer(admin);

        mockMvc.perform(get("/api/usuarios").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CLAVE_PENDIENTE"));

        mockMvc.perform(get("/api/auth/yo").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    void losMensajesDe401y403LlevanLaCodificacionUtf8() throws Exception {
        Usuario admin = crearUsuario("sesion.utf8@example.com", Rol.RRHH_ADMIN);
        admin.setDebeCambiarClave(true);
        usuarioRepository.save(admin);

        // 403 del filtro (clave pendiente): "contraseña" debe viajar como UTF-8, no como ISO-8859-1.
        var pendiente = mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(pendiente.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        org.assertj.core.api.Assertions.assertThat(new String(pendiente.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("contraseña");

        // 403 de autorización por rol: "operación".
        Usuario supervisor = crearUsuario("sesion.utf8.sup@example.com", Rol.SUPERVISOR);
        var sinPermiso = mockMvc.perform(post("/api/usuarios/1/activar").header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(new String(sinPermiso.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("operación");
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Usuario crearUsuario(String correo, String nombreRol) {
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
}
