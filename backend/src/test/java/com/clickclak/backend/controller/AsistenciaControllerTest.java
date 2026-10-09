package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Asignacion;
import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.EstadoValidacion;
import com.clickclak.backend.model.Horario;
import com.clickclak.backend.model.Marcacion;
import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.TipoEvento;
import com.clickclak.backend.model.Ubicacion;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.AsignacionRepository;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.HorarioRepository;
import com.clickclak.backend.repository.MarcacionRepository;
import com.clickclak.backend.repository.ProyectoRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UbicacionRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;

/**
 * Consulta de asistencia contra la base real (PostGIS incluido): filtros combinables, bordes de día
 * en hora de Lima, paginación, resumen por estado y permisos por rol. Las fechas de prueba son de
 * 2020 y cada prueba filtra por sus propios usuarios o proyecto, para no mezclarse con datos del
 * desarrollo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AsistenciaControllerTest {

    private static final GeometryFactory FABRICA = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private DispositivoRepository dispositivoRepository;
    @Autowired private MarcacionRepository marcacionRepository;
    @Autowired private JwtService jwtService;

    /** Un colaborador con su dispositivo y una asignación en una sede de un proyecto propio. */
    private record Escenario(Usuario colaborador, Dispositivo dispositivo, Proyecto proyecto, Ubicacion sede, Asignacion asignacion) {
    }

    @Test
    void listar_devuelveLosDatosDeSupervisionDeLaMasRecienteALaMasAntigua() throws Exception {
        Usuario admin = crearUsuario("asis.admin1@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000001");
        Escenario e = crearEscenario("asis.col1@example.com", "92000002", "Proyecto Uno", "Sede Uno");
        marcar(e, TipoEvento.ENTRADA, Instant.parse("2020-01-15T13:00:00Z"), EstadoValidacion.VALIDO, "12.50");
        marcar(e, TipoEvento.SALIDA, Instant.parse("2020-01-15T22:00:00Z"), EstadoValidacion.OBSERVADO, "170.00");

        mockMvc.perform(get("/api/marcaciones").param("proyectoId", e.proyecto().getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido[0].tipoEvento").value("SALIDA"))
                .andExpect(jsonPath("$.contenido[0].estadoValidacion").value("OBSERVADO"))
                .andExpect(jsonPath("$.contenido[1].tipoEvento").value("ENTRADA"))
                .andExpect(jsonPath("$.contenido[1].nombreUsuario").value("Persona asis.col1@example.com"))
                .andExpect(jsonPath("$.contenido[1].usuarioId").value(e.colaborador().getId()))
                .andExpect(jsonPath("$.contenido[1].proyecto").value("Proyecto Uno"))
                .andExpect(jsonPath("$.contenido[1].ubicacion").value("Sede Uno"))
                .andExpect(jsonPath("$.contenido[1].radioToleranciaMetros").value(150))
                .andExpect(jsonPath("$.contenido[1].dispositivo").value("Teléfono de asis.col1@example.com"))
                .andExpect(jsonPath("$.contenido[1].distanciaMetros").value(12.5))
                .andExpect(jsonPath("$.contenido[1].latitud").value(-12.0464))
                .andExpect(jsonPath("$.contenido[1].longitud").value(-77.0428));
    }

    @Test
    void listar_losDiasSeCuentanEnHoraDeLima() throws Exception {
        Usuario admin = crearUsuario("asis.admin2@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000011");
        Escenario e = crearEscenario("asis.col2@example.com", "92000012", "Proyecto Dos", "Sede Dos");
        // 04:59 UTC = 23:59 del 14 en Lima; 05:00 UTC = 00:00 del 15; 04:59 UTC del 16 = 23:59 del 15.
        marcar(e, TipoEvento.SALIDA, Instant.parse("2020-01-15T04:59:00Z"), EstadoValidacion.VALIDO, "1.00");
        marcar(e, TipoEvento.ENTRADA, Instant.parse("2020-01-15T05:00:00Z"), EstadoValidacion.VALIDO, "1.00");
        marcar(e, TipoEvento.SALIDA, Instant.parse("2020-01-16T04:59:00Z"), EstadoValidacion.VALIDO, "1.00");
        marcar(e, TipoEvento.ENTRADA, Instant.parse("2020-01-16T05:00:00Z"), EstadoValidacion.VALIDO, "1.00");

        mockMvc.perform(get("/api/marcaciones").param("usuarioId", e.colaborador().getId().toString())
                        .param("desde", "2020-01-15").param("hasta", "2020-01-15")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido[0].horaEvento").value("2020-01-16T04:59:00Z"))
                .andExpect(jsonPath("$.contenido[1].horaEvento").value("2020-01-15T05:00:00Z"));

        // Sin "hasta" llega hasta el final de los tiempos; sin "desde", desde el principio.
        mockMvc.perform(get("/api/marcaciones").param("usuarioId", e.colaborador().getId().toString())
                        .param("desde", "2020-01-16").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/marcaciones").param("usuarioId", e.colaborador().getId().toString())
                        .param("hasta", "2020-01-14").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listar_filtraPorPersonaEstadoTipoProyectoYSede() throws Exception {
        Usuario admin = crearUsuario("asis.admin3@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000021");
        Escenario uno = crearEscenario("asis.col3@example.com", "92000022", "Proyecto Tres", "Sede Tres");
        Escenario dos = crearEscenario("asis.col4@example.com", "92000023", "Proyecto Cuatro", "Sede Cuatro");
        marcar(uno, TipoEvento.ENTRADA, Instant.parse("2020-02-10T13:00:00Z"), EstadoValidacion.VALIDO, "5.00");
        marcar(uno, TipoEvento.SALIDA, Instant.parse("2020-02-10T22:00:00Z"), EstadoValidacion.SOSPECHOSO, "900.00");
        marcar(dos, TipoEvento.ENTRADA, Instant.parse("2020-02-10T13:05:00Z"), EstadoValidacion.FUERA_DE_TOLERANCIA, "1000.00");

        mockMvc.perform(get("/api/marcaciones").param("usuarioId", uno.colaborador().getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/marcaciones").param("usuarioId", uno.colaborador().getId().toString())
                        .param("estado", "SOSPECHOSO").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].tipoEvento").value("SALIDA"));
        mockMvc.perform(get("/api/marcaciones").param("proyectoId", dos.proyecto().getId().toString())
                        .param("tipoEvento", "ENTRADA").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].estadoValidacion").value("FUERA_DE_TOLERANCIA"));
        mockMvc.perform(get("/api/marcaciones").param("ubicacionId", uno.sede().getId().toString())
                        .param("tipoEvento", "SALIDA").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/marcaciones").param("ubicacionId", uno.sede().getId().toString())
                        .param("proyectoId", dos.proyecto().getId().toString()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listar_unaMarcacionSinAsignacionSeMuestraSinSedeNiProyecto() throws Exception {
        Usuario admin = crearUsuario("asis.admin4@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000031");
        Escenario e = crearEscenario("asis.col5@example.com", "92000032", "Proyecto Cinco", "Sede Cinco");
        marcacionRepository.save(Marcacion.builder()
                .uuidCliente(UUID.randomUUID()).usuario(e.colaborador()).asignacion(null).dispositivo(e.dispositivo())
                .tipoEvento(TipoEvento.ENTRADA).horaEvento(Instant.parse("2020-03-01T13:00:00Z"))
                .geom(punto()).precisionMetros(new BigDecimal("8.00")).distanciaMetros(null)
                .estadoValidacion(EstadoValidacion.SIN_ASIGNACION).build());

        mockMvc.perform(get("/api/marcaciones").param("usuarioId", e.colaborador().getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].estadoValidacion").value("SIN_ASIGNACION"))
                .andExpect(jsonPath("$.contenido[0].proyecto").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].ubicacion").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].distanciaMetros").doesNotExist());

        // Filtrar por sede o proyecto la excluye: no pertenece a ninguna.
        mockMvc.perform(get("/api/marcaciones").param("usuarioId", e.colaborador().getId().toString())
                        .param("ubicacionId", e.sede().getId().toString()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listar_indicaCuantoTardoEnSincronizarseUnaMarcacionSinConexion() throws Exception {
        Usuario admin = crearUsuario("asis.admin5@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000041");
        Escenario e = crearEscenario("asis.col6@example.com", "92000042", "Proyecto Seis", "Sede Seis");
        // La hora de sincronización la fija la base al insertar (ahora): el evento fue hace dos horas.
        marcar(e, TipoEvento.ENTRADA, Instant.now().minusSeconds(7200), EstadoValidacion.VALIDO, "3.00");

        mockMvc.perform(get("/api/marcaciones").param("usuarioId", e.colaborador().getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.contenido[0].retrasoSincronizacionSegundos")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.greaterThanOrEqualTo(7190),
                                org.hamcrest.Matchers.lessThan(7300))));
    }

    @Test
    void listar_paginaYRechazaTamanosInvalidos() throws Exception {
        Usuario admin = crearUsuario("asis.admin6@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000051");
        Escenario e = crearEscenario("asis.col7@example.com", "92000052", "Proyecto Siete", "Sede Siete");
        for (int i = 0; i < 5; i++) {
            marcar(e, TipoEvento.ENTRADA, Instant.parse("2020-04-01T13:00:00Z").plusSeconds(60L * i), EstadoValidacion.VALIDO, "1.00");
        }

        mockMvc.perform(get("/api/marcaciones").param("usuarioId", e.colaborador().getId().toString())
                        .param("tamano", "2").param("pagina", "2").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(1));

        mockMvc.perform(get("/api/marcaciones").param("tamano", "0").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/marcaciones").param("tamano", "101").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/marcaciones").param("pagina", "-1").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_rechazaFiltrosMalFormados() throws Exception {
        Usuario admin = crearUsuario("asis.admin7@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000061");

        mockMvc.perform(get("/api/marcaciones").param("desde", "2020-05-10").param("hasta", "2020-05-09")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/marcaciones").param("estado", "INEXISTENTE").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/marcaciones").param("desde", "15/01/2020").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resumen_repartePorEstadoIgnorandoElFiltroDeEstado() throws Exception {
        Usuario admin = crearUsuario("asis.admin8@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000071");
        Escenario e = crearEscenario("asis.col8@example.com", "92000072", "Proyecto Ocho", "Sede Ocho");
        marcar(e, TipoEvento.ENTRADA, Instant.parse("2020-06-01T13:00:00Z"), EstadoValidacion.VALIDO, "1.00");
        marcar(e, TipoEvento.SALIDA, Instant.parse("2020-06-01T22:00:00Z"), EstadoValidacion.VALIDO, "1.00");
        marcar(e, TipoEvento.ENTRADA, Instant.parse("2020-06-02T13:00:00Z"), EstadoValidacion.OBSERVADO, "160.00");
        marcar(e, TipoEvento.ENTRADA, Instant.parse("2020-06-03T13:00:00Z"), EstadoValidacion.SOSPECHOSO, "700.00");

        mockMvc.perform(get("/api/marcaciones/resumen").param("usuarioId", e.colaborador().getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.porEstado.VALIDO").value(2))
                .andExpect(jsonPath("$.porEstado.OBSERVADO").value(1))
                .andExpect(jsonPath("$.porEstado.SOSPECHOSO").value(1))
                .andExpect(jsonPath("$.porEstado.FUERA_DE_TOLERANCIA").value(0))
                .andExpect(jsonPath("$.porEstado.SIN_ASIGNACION").value(0));

        // El resumen respeta fechas y tipo, pero no el estado: sigue repartiendo entre todos.
        mockMvc.perform(get("/api/marcaciones/resumen").param("usuarioId", e.colaborador().getId().toString())
                        .param("desde", "2020-06-01").param("hasta", "2020-06-01").param("tipoEvento", "ENTRADA")
                        .param("estado", "SOSPECHOSO").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.porEstado.VALIDO").value(1));
    }

    @Test
    void permisos_soloSupervisionConsulta() throws Exception {
        Usuario admin = crearUsuario("asis.admin9@example.com", Rol.RRHH_ADMIN, "Ada", "Admin", "92000081");
        Usuario supervisor = crearUsuario("asis.sup9@example.com", Rol.SUPERVISOR, "Sara", "Supervisora", "92000082");
        Escenario e = crearEscenario("asis.col9@example.com", "92000083", "Proyecto Nueve", "Sede Nueve");

        for (String ruta : new String[] {"/api/marcaciones", "/api/marcaciones/resumen"}) {
            mockMvc.perform(get(ruta).header("Authorization", bearer(admin))).andExpect(status().isOk());
            mockMvc.perform(get(ruta).header("Authorization", bearer(supervisor))).andExpect(status().isOk());
            mockMvc.perform(get(ruta).header("Authorization", bearer(e.colaborador()))).andExpect(status().isForbidden());
            mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Point punto() {
        return FABRICA.createPoint(new Coordinate(-77.0428, -12.0464));
    }

    private void marcar(Escenario e, TipoEvento tipo, Instant horaEvento, EstadoValidacion estado, String distancia) {
        marcacionRepository.save(Marcacion.builder()
                .uuidCliente(UUID.randomUUID()).usuario(e.colaborador()).asignacion(e.asignacion()).dispositivo(e.dispositivo())
                .tipoEvento(tipo).horaEvento(horaEvento).geom(punto())
                .precisionMetros(new BigDecimal("10.00")).distanciaMetros(new BigDecimal(distancia))
                .estadoValidacion(estado).build());
    }

    private Usuario crearUsuario(String correo, String nombreRol, String nombres, String apellidos, String documento) {
        Rol rol = rolRepository.findByNombre(nombreRol).orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .nombres(nombres).apellidos(apellidos)
                .tipoDocumento("DNI").numeroDocumento(documento)
                .correo(correo)
                .passwordHash(Rol.COLABORADOR.equals(nombreRol) ? null : "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvwxyzABCDE")
                .rol(rol).activo(true).build());
    }

    private Escenario crearEscenario(String correo, String documento, String nombreProyecto, String nombreSede) {
        Usuario colaborador = crearUsuario(correo, Rol.COLABORADOR, "Persona", correo, documento);
        Proyecto proyecto = proyectoRepository.save(Proyecto.builder()
                .nombre(nombreProyecto).cliente("Cliente Demo").fechaInicio(LocalDate.of(2020, 1, 1)).build());
        Ubicacion sede = ubicacionRepository.save(Ubicacion.builder()
                .proyecto(proyecto).nombre(nombreSede).geom(punto()).radioToleranciaMetros(150).build());
        Horario horario = horarioRepository.save(Horario.builder()
                .nombre("Turno " + correo).horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(17, 0))
                .toleranciaMinutos(10).build());
        Asignacion asignacion = asignacionRepository.save(Asignacion.builder()
                .usuario(colaborador).proyecto(proyecto).ubicacion(sede).horario(horario)
                .fechaInicio(LocalDate.of(2020, 1, 1)).build());
        Dispositivo dispositivo = dispositivoRepository.save(Dispositivo.builder()
                .usuario(colaborador).credentialId("cred-" + correo).clavePublica("clave-publica-demo")
                .nombreDispositivo("Teléfono de " + correo).activo(true).build());
        return new Escenario(colaborador, dispositivo, proyecto, sede, asignacion);
    }
}
