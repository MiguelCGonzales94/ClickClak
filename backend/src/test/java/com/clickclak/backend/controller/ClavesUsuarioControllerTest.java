package com.clickclak.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
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
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenIntentosFallidos;
import com.clickclak.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HU04, contraseñas de punta a punta contra la base real y el BCrypt real: restablecimiento por el
 * administrador, ingreso con clave temporal, cambio obligatorio y cambio voluntario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClavesUsuarioControllerTest {

    private static final String CLAVE_ACTUAL = "ClaveActual2468";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private BitacoraAuditoriaRepository bitacoraRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AlmacenIntentosFallidos almacenIntentosFallidos;

    private String correoAfectado;

    @AfterEach
    void limpiarBloqueos() {
        if (correoAfectado != null) {
            almacenIntentosFallidos.limpiar(correoAfectado);
        }
    }

    @Test
    void flujoCompleto_restablecerIngresarConTemporalYCambiarla() throws Exception {
        Usuario admin = crearUsuario("claves.admin1@example.com", Rol.RRHH_ADMIN, "91000201");
        Usuario supervisor = crearUsuario("claves.supervisor1@example.com", Rol.SUPERVISOR, "91000202");
        correoAfectado = supervisor.getCorreo();

        // 1. El administrador restablece: la clave temporal llega una sola vez y sin caché.
        String claveTemporal = restablecer(admin, supervisor);
        assertThat(claveTemporal).hasSize(12);

        // 2. El supervisor entra con la temporal y el login avisa que debe cambiarla.
        String tokenTemporal = iniciarSesion(supervisor.getCorreo(), claveTemporal, true);

        // 3. Con la clave pendiente solo puede cambiarla; el resto de la API responde 403.
        mockMvc.perform(get("/api/usuarios").header("Authorization", tokenTemporal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CLAVE_PENDIENTE"));
        mockMvc.perform(get("/api/auth/yo").header("Authorization", tokenTemporal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarClave").value(true));

        // 4. La cambia: el token usado queda revocado y la clave temporal deja de servir.
        mockMvc.perform(post("/api/auth/cambiar-clave").header("Authorization", tokenTemporal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claveActual\":\"" + claveTemporal + "\",\"claveNueva\":\"ClaveNueva9753\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/yo").header("Authorization", tokenTemporal))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + supervisor.getCorreo() + "\",\"password\":\"" + claveTemporal + "\"}"))
                .andExpect(status().isUnauthorized());

        // 5. Con la nueva entra normal, sin marca pendiente, y la bitácora no guarda ninguna clave.
        String tokenNormal = iniciarSesion(supervisor.getCorreo(), "ClaveNueva9753", false);
        mockMvc.perform(get("/api/usuarios").header("Authorization", tokenNormal)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/yo").header("Authorization", tokenNormal))
                .andExpect(jsonPath("$.debeCambiarClave").value(false));

        bitacoraRepository.findByEntidadAndEntidadId("usuario", supervisor.getId()).forEach(entrada -> {
            assertThat(String.valueOf(entrada.getValoresNuevos())).doesNotContain(claveTemporal).doesNotContain("ClaveNueva9753");
            assertThat(String.valueOf(entrada.getValoresAnteriores())).doesNotContain(claveTemporal);
        });
    }

    @Test
    void restablecer_levantaElBloqueoDelUsuario() throws Exception {
        Usuario admin = crearUsuario("claves.admin2@example.com", Rol.RRHH_ADMIN, "91000211");
        Usuario supervisor = crearUsuario("claves.supervisor2@example.com", Rol.SUPERVISOR, "91000212");
        correoAfectado = supervisor.getCorreo();
        for (int i = 0; i < 5; i++) {
            almacenIntentosFallidos.registrarFallo(supervisor.getCorreo());
        }

        restablecer(admin, supervisor);

        mockMvc.perform(get("/api/usuarios/" + supervisor.getId()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.estadoCuenta").value("CLAVE_PENDIENTE"));
    }

    @Test
    void restablecer_casosNoPermitidos() throws Exception {
        Usuario admin = crearUsuario("claves.admin3@example.com", Rol.RRHH_ADMIN, "91000221");
        Usuario colaborador = crearUsuario("claves.colaborador3@example.com", Rol.COLABORADOR, "91000222");
        Usuario inactivo = crearUsuario("claves.inactivo3@example.com", Rol.SUPERVISOR, "91000223");
        inactivo.setActivo(false);
        usuarioRepository.save(inactivo);
        Usuario supervisor = crearUsuario("claves.supervisor3@example.com", Rol.SUPERVISOR, "91000224");

        mockMvc.perform(post("/api/usuarios/" + colaborador.getId() + "/restablecer-clave").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/usuarios/" + inactivo.getId() + "/restablecer-clave").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/usuarios/" + admin.getId() + "/restablecer-clave").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/usuarios/" + supervisor.getId() + "/restablecer-clave").header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/usuarios/999999/restablecer-clave").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cambioVoluntario_exigeLaActualYUnaNuevaValida() throws Exception {
        Usuario supervisor = crearUsuario("claves.supervisor4@example.com", Rol.SUPERVISOR, "91000231");
        correoAfectado = supervisor.getCorreo();
        String token = bearer(supervisor);

        cambiar(token, "incorrecta123", "ClaveNueva9753", 400);
        cambiar(token, CLAVE_ACTUAL, "corta", 400);
        cambiar(token, CLAVE_ACTUAL, "soloLetrasAqui", 400);
        cambiar(token, CLAVE_ACTUAL, CLAVE_ACTUAL, 400);
        cambiar(token, CLAVE_ACTUAL, "ClaveNueva9753", 200);
    }

    @Test
    void cambioVoluntario_cincoFallosBloqueanLaCuenta() throws Exception {
        Usuario supervisor = crearUsuario("claves.supervisor5@example.com", Rol.SUPERVISOR, "91000241");
        correoAfectado = supervisor.getCorreo();
        String token = bearer(supervisor);

        for (int i = 0; i < 5; i++) {
            cambiar(token, "incorrecta123", "ClaveNueva9753", 400);
        }
        cambiar(token, CLAVE_ACTUAL, "ClaveNueva9753", 429);
    }

    @Test
    void cambiarClave_sinSesion_respondeNoAutenticado() throws Exception {
        mockMvc.perform(post("/api/auth/cambiar-clave").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claveActual\":\"x\",\"claveNueva\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String restablecer(Usuario admin, Usuario objetivo) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/usuarios/" + objetivo.getId() + "/restablecer-clave")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("claveTemporal").asText();
    }

    private String iniciarSesion(String correo, String password, boolean debeCambiar) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarClave").value(debeCambiar))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(cuerpo).get("token").asText();
    }

    private void cambiar(String token, String actual, String nueva, int estadoEsperado) throws Exception {
        mockMvc.perform(post("/api/auth/cambiar-clave").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claveActual\":\"" + actual + "\",\"claveNueva\":\"" + nueva + "\"}"))
                .andExpect(status().is(estadoEsperado));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Usuario crearUsuario(String correo, String nombreRol, String documento) {
        Rol rol = rolRepository.findByNombre(nombreRol).orElseThrow();
        boolean esColaborador = Rol.COLABORADOR.equals(nombreRol);
        return usuarioRepository.save(Usuario.builder()
                .nombres("Prueba").apellidos("Claves")
                .tipoDocumento("DNI").numeroDocumento(documento)
                .correo(correo)
                .passwordHash(esColaborador ? null : passwordEncoder.encode(CLAVE_ACTUAL))
                .rol(rol)
                .activo(true)
                .build());
    }
}
