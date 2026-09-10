package com.clickclak.backend.controller;

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
import com.clickclak.backend.security.AlmacenTokensRecuperacion;
import com.clickclak.backend.security.JwtService;

/** HU02 (logout que revoca de verdad) y HU03 (recuperación de clave) de punta a punta. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutenticacionSesionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private AlmacenTokensRecuperacion almacenTokensRecuperacion;

    @Test
    void logout_revocaElTokenYLasLlamadasPosterioresFallan() throws Exception {
        Usuario supervisor = crearSupervisor("supervisor.sesion1@example.com", "claveSegura123");
        String token = "Bearer " + jwtService.generarToken(supervisor);

        mockMvc.perform(get("/api/auth/yo").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/yo").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void solicitarRecuperacion_devuelveSiempreElMismoMensaje_existaONoElCorreo() throws Exception {
        crearSupervisor("supervisor.sesion2@example.com", "claveSegura123");

        String mensajeConCuenta = mockMvc.perform(post("/api/auth/recuperacion/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"supervisor.sesion2@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String mensajeSinCuenta = mockMvc.perform(post("/api/auth/recuperacion/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"nadie.sesion2@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(mensajeConCuenta).isEqualTo(mensajeSinCuenta);
    }

    @Test
    void restablecerClave_conTokenValido_permiteIniciarSesionConLaClaveNueva() throws Exception {
        Usuario supervisor = crearSupervisor("supervisor.sesion3@example.com", "claveVieja123");
        String tokenRecuperacion = almacenTokensRecuperacion.generar(supervisor.getId());

        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenRecuperacion + "\",\"nuevaPassword\":\"claveNueva456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"supervisor.sesion3@example.com\",\"password\":\"claveNueva456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"supervisor.sesion3@example.com\",\"password\":\"claveVieja123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restablecerClave_reutilizandoElMismoToken_fallaLaSegundaVez() throws Exception {
        Usuario supervisor = crearSupervisor("supervisor.sesion4@example.com", "claveVieja123");
        String tokenRecuperacion = almacenTokensRecuperacion.generar(supervisor.getId());
        String cuerpo = "{\"token\":\"" + tokenRecuperacion + "\",\"nuevaPassword\":\"claveNueva456\"}";

        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restablecerClave_conPoliticaIncumplida_devuelve400() throws Exception {
        Usuario supervisor = crearSupervisor("supervisor.sesion5@example.com", "claveVieja123");
        String tokenRecuperacion = almacenTokensRecuperacion.generar(supervisor.getId());

        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenRecuperacion + "\",\"nuevaPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest());
    }

    private Usuario crearSupervisor(String correo, String password) {
        Rol rol = rolRepository.findByNombre(Rol.SUPERVISOR).orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .nombres("Ana").apellidos("Pérez")
                .tipoDocumento("DNI").numeroDocumento(Integer.toString(correo.hashCode()))
                .correo(correo)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build());
    }
}
