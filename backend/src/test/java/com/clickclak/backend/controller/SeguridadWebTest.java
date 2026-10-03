package com.clickclak.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Controles de seguridad web de la API (cap. VI 6.3, CLICKCLACK-57): respuestas de error que no
 * filtran detalles internos, bloqueo por fuerza bruta, CORS restringido, mínimo privilegio en
 * los catálogos y validación de las entradas. Contra la base real, como el resto de los
 * controladores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeguridadWebTest {

    /** Endpoint que solo existe en las pruebas, para provocar un fallo interno real. */
    @TestConfiguration
    static class ConfiguracionDePrueba {
        @Bean
        ControladorQueFalla controladorQueFalla() {
            return new ControladorQueFalla();
        }
    }

    @RestController
    @RequestMapping("/api/prueba-seguridad")
    static class ControladorQueFalla {
        @GetMapping("/falla")
        String falla() {
            throw new IllegalStateException("detalle-interno-secreto jdbc:postgresql://basededatos:5432/clickclak");
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JwtService jwtService;

    private Usuario colaborador;
    private Usuario supervisor;
    private Usuario rrhh;

    @BeforeEach
    void crearUsuarios() {
        colaborador = crearUsuario("seguridad.colaborador@prueba.local", "91000001", Rol.COLABORADOR);
        supervisor = crearUsuario("seguridad.supervisor@prueba.local", "91000002", Rol.SUPERVISOR);
        rrhh = crearUsuario("seguridad.rrhh@prueba.local", "91000003", Rol.RRHH_ADMIN);
    }

    // --- errores sin detalles internos ------------------------------------------------------

    @Test
    void unFalloInternoSaleComo500GenericoSinDetalles() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/prueba-seguridad/falla").header(HttpHeaders.AUTHORIZATION, bearer(supervisor)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"))
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("detalle-interno-secreto", "jdbc", "postgresql", "Exception", "at com.");
    }

    @Test
    void jsonMalFormadoDevuelve400SinNombrarClasesInternas() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"correo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo de la solicitud no es válido"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("Jackson", "com.fasterxml", "JsonParseException");
    }

    @Test
    void metodoNoPermitidoDevuelve405Uniforme() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Método no permitido"));
    }

    @Test
    void rutaInexistenteDevuelve404Uniforme() throws Exception {
        mockMvc.perform(get("/api/no-existe").header(HttpHeaders.AUTHORIZATION, bearer(supervisor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
    }

    @Test
    void parametroConTipoInvalidoDevuelve400SinEcoDelValor() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/incidencias").param("estado", "<script>alert(1)</script>")
                        .header(HttpHeaders.AUTHORIZATION, bearer(supervisor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Solicitud inválida"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("script");
    }

    @Test
    void tipoDeContenidoNoSoportadoDevuelve415() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("correo=a@b.c"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Tipo de contenido no soportado"));
    }

    @Test
    void lasRespuestasDeErrorLlevanCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/api/incidencias/mias"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
    }

    // --- fuerza bruta -----------------------------------------------------------------------

    @Test
    void tras5LoginsFallidosLaCuentaQuedaBloqueadaConRetryAfter() throws Exception {
        String correo = "bloqueo." + UUID.randomUUID() + "@prueba.local";
        for (int i = 0; i < 5; i++) {
            iniciarSesion(correo, "incorrecta123").andExpect(status().isUnauthorized());
        }

        iniciarSesion(correo, "incorrecta123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").exists());

        // Otra cuenta no se ve afectada.
        iniciarSesion("otra." + UUID.randomUUID() + "@prueba.local", "incorrecta123").andExpect(status().isUnauthorized());
    }

    // --- CORS -------------------------------------------------------------------------------

    @Test
    void corsAutorizaElOrigenConfiguradoYRechazaOtrosOrigenes() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // --- mínimo privilegio y superficie expuesta --------------------------------------------

    @Test
    void elColaboradorNoListaLosCatalogosDeLaOrganizacion() throws Exception {
        for (String ruta : new String[] {"/api/proyectos", "/api/ubicaciones", "/api/horarios"}) {
            mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(colaborador)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(supervisor)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void lasMetricasDeActuatorExigenAutenticacion() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // --- validación de entradas -------------------------------------------------------------

    @Test
    void elLoginRechazaCamposDesmedidos() throws Exception {
        String correoLargo = "a".repeat(145) + "@example.com";
        iniciarSesion(correoLargo, "clave12345").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("correo")));
        iniciarSesion("ana@example.com", "x".repeat(73)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    void elAltaDeUsuarioRechazaTamanosQueNoCabenEnLaBase() throws Exception {
        Map<String, Object> cuerpo = Map.of(
                "nombres", "N".repeat(101), "apellidos", "Pérez", "tipoDocumento", "DNI", "numeroDocumento", "12345678",
                "correo", "nuevo@prueba.local", "rol", "COLABORADOR");

        mockMvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(rrhh))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("nombres")));
    }

    @Test
    void elAltaDeHorarioRechazaDiasDeSemanaInvalidos() throws Exception {
        Map<String, Object> cuerpo = Map.of("nombre", "Turno", "horaInicio", "08:00", "horaFin", "17:00",
                "toleranciaMinutos", 10, "diasSemana", "lunes;DROP TABLE");

        mockMvc.perform(post("/api/horarios").header(HttpHeaders.AUTHORIZATION, bearer(supervisor))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("diasSemana")));
    }

    @Test
    void laMarcacionRechazaCoordenadasFueraDeRangoYCamposFaltantes() throws Exception {
        Map<String, Object> coordenadasInvalidas = Map.of(
                "uuidCliente", UUID.randomUUID().toString(), "usuarioId", colaborador.getId(), "dispositivoId", 1,
                "tipoEvento", "ENTRADA", "horaEvento", Instant.now().toString(), "latitud", 95.0, "longitud", -77.0);
        mockMvc.perform(post("/api/marcaciones").header(HttpHeaders.AUTHORIZATION, bearer(colaborador))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(coordenadasInvalidas)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("latitud")));

        Map<String, Object> sinIdentificador = Map.of(
                "usuarioId", colaborador.getId(), "dispositivoId", 1, "tipoEvento", "ENTRADA",
                "horaEvento", Instant.now().toString(), "latitud", -12.0, "longitud", -77.0);
        mockMvc.perform(post("/api/marcaciones").header(HttpHeaders.AUTHORIZATION, bearer(colaborador))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(sinIdentificador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("uuidCliente")));
    }

    @Test
    void laMarcacionRechazaUnEventoDelFuturo() throws Exception {
        Map<String, Object> cuerpo = Map.of(
                "uuidCliente", UUID.randomUUID().toString(), "usuarioId", colaborador.getId(), "dispositivoId", 1,
                "tipoEvento", "ENTRADA", "horaEvento", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "latitud", -12.0, "longitud", -77.0);

        mockMvc.perform(post("/api/marcaciones").header(HttpHeaders.AUTHORIZATION, bearer(colaborador))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("futura")));
    }

    // --- apoyo ------------------------------------------------------------------------------

    private ResultActions iniciarSesion(String correo, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("correo", correo, "password", password))));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Usuario crearUsuario(String correo, String documento, String rol) {
        return usuarioRepository.save(Usuario.builder()
                .nombres("Prueba").apellidos(rol).tipoDocumento("DNI").numeroDocumento(documento)
                .correo(correo).rol(rolRepository.findByNombre(rol).orElseThrow()).activo(true).build());
    }
}
