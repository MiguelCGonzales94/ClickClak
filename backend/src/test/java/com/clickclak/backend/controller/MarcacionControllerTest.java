package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.RegistrarMarcacionRequest;
import com.clickclak.backend.model.Asignacion;
import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.TipoEvento;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.HorarioRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Prueba el endpoint de marcación contra la base real (incluye PostGIS): un colaborador
 * puede registrar su propia asistencia, y el sistema rechaza intentos de marcar en nombre
 * de otro usuario aunque el token JWT presentado sea válido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarcacionControllerTest {

    private static final GeometryFactory FABRICA_GEOMETRIA = new GeometryFactory(new PrecisionModel(), 4326);
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private DispositivoRepository dispositivoRepository;
    @Autowired private JwtService jwtService;

    @Test
    void registrarMarcacionPropia_devuelveCreadaYValida() throws Exception {
        Usuario tecnico = crearTecnicoConAsignacionYDispositivo("tecnico1@example.com");
        Dispositivo dispositivo = dispositivoRepository.findByUsuarioIdAndActivoTrue(tecnico.getId()).get(0);
        String token = jwtService.generarToken(tecnico);

        RegistrarMarcacionRequest solicitud = new RegistrarMarcacionRequest(
                UUID.randomUUID(), tecnico.getId(), dispositivo.getId(), TipoEvento.ENTRADA,
                Instant.now(), -12.0464, -77.0428, BigDecimal.valueOf(10));

        mockMvc.perform(post("/api/marcaciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoValidacion").value("VALIDO"));
    }

    @Test
    void registrarMarcacionDeOtroUsuario_devuelve403() throws Exception {
        Usuario tecnico = crearTecnicoConAsignacionYDispositivo("tecnico2@example.com");
        Usuario otroTecnico = crearTecnicoConAsignacionYDispositivo("tecnico3@example.com");
        Dispositivo dispositivo = dispositivoRepository.findByUsuarioIdAndActivoTrue(tecnico.getId()).get(0);
        String token = jwtService.generarToken(tecnico);

        RegistrarMarcacionRequest solicitud = new RegistrarMarcacionRequest(
                UUID.randomUUID(), otroTecnico.getId(), dispositivo.getId(), TipoEvento.ENTRADA,
                Instant.now(), -12.0464, -77.0428, BigDecimal.valueOf(10));

        mockMvc.perform(post("/api/marcaciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    private Usuario crearTecnicoConAsignacionYDispositivo(String correo) {
        Rol rolColaborador = rolRepository.findByNombre(Rol.COLABORADOR).orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombres("Ana").apellidos("Pérez")
                .tipoDocumento("DNI").numeroDocumento(Integer.toString(correo.hashCode()))
                .correo(correo)
                .rol(rolColaborador)
                .activo(true)
                .build());

        Proyecto proyecto = proyectoRepository.save(Proyecto.builder()
                .nombre("Proyecto Demo").cliente("Cliente Demo")
                .fechaInicio(LocalDate.now(ZONA_LIMA)).build());

        Point puntoUbicacion = FABRICA_GEOMETRIA.createPoint(new Coordinate(-77.0428, -12.0464));
        Ubicacion ubicacion = ubicacionRepository.save(Ubicacion.builder()
                .proyecto(proyecto).nombre("Sede Demo").geom(puntoUbicacion)
                .radioToleranciaMetros(150).build());

        // Tolerancia de 24h desde medianoche: el test es determinista sin importar la hora
        // real en la que corra, porque ninguna entrada puede quedar "tarde".
        Horario horario = horarioRepository.save(Horario.builder()
                .nombre("Turno Demo")
                .horaInicio(LocalTime.MIDNIGHT).horaFin(LocalTime.of(23, 59))
                .toleranciaMinutos(24 * 60)
                .build());

        asignacionRepository.save(Asignacion.builder()
                .usuario(usuario).proyecto(proyecto).ubicacion(ubicacion).horario(horario)
                .fechaInicio(LocalDate.now(ZONA_LIMA)).build());

        dispositivoRepository.save(Dispositivo.builder()
                .usuario(usuario).credentialId("cred-" + correo)
                .clavePublica("clave-publica-demo").activo(true).build());

        return usuario;
    }
}
