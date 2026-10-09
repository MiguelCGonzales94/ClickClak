package com.clickclak.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
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
import com.clickclak.backend.security.AlmacenIntentosFallidos;
import com.clickclak.backend.security.JwtService;

/**
 * HU04, gestión completa contra la base real: búsqueda paginada, estado de cuenta, baja con
 * motivo, desbloqueo, eliminación (solo sin historial) e historial por usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioGestionControllerTest {

    private static final String CLAVE_FICTICIA = "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvwxyzABCDE";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JwtService jwtService;
    @Autowired private AlmacenIntentosFallidos almacenIntentosFallidos;

    private String correoBloqueado;

    @AfterEach
    void limpiarBloqueo() {
        if (correoBloqueado != null) {
            almacenIntentosFallidos.limpiar(correoBloqueado);
        }
    }

    @Test
    void buscar_porTextoRolYPagina_devuelveSoloLoQueCoincide() throws Exception {
        Usuario admin = crearUsuario("gestion.admin@example.com", Rol.RRHH_ADMIN, "Zoila", "Gestionadora", "91000001");
        crearUsuario("gestion.uno@example.com", Rol.COLABORADOR, "Zeta", "Alfa", "91000002");
        crearUsuario("gestion.dos@example.com", Rol.COLABORADOR, "Zeta", "Beta", "91000003");
        crearUsuario("gestion.tres@example.com", Rol.SUPERVISOR, "Zeta", "Gamma", "91000004");

        mockMvc.perform(get("/api/usuarios/buscar").param("q", "ZETA").param("rol", Rol.COLABORADOR)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido[0].apellidos").value("Alfa"))
                .andExpect(jsonPath("$.contenido[1].apellidos").value("Beta"));

        mockMvc.perform(get("/api/usuarios/buscar").param("q", "zeta").param("tamano", "2").param("pagina", "1")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(1));

        // Busca también por documento y por correo.
        mockMvc.perform(get("/api/usuarios/buscar").param("q", "91000004").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/usuarios/buscar").param("q", "gestion.dos@").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void buscar_loscomodinesDelTextoSeTratanComoLiterales() throws Exception {
        Usuario admin = crearUsuario("literal.admin@example.com", Rol.RRHH_ADMIN, "Lidia", "Literal", "91000011");

        mockMvc.perform(get("/api/usuarios/buscar").param("q", "%").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/usuarios/buscar").param("q", "_").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void buscar_porEstado_respetaLaPrecedenciaDeEstados() throws Exception {
        Usuario admin = crearUsuario("estados.admin@example.com", Rol.RRHH_ADMIN, "Eva", "Estados", "91000021");
        Usuario inactivo = crearUsuario("estados.inactivo@example.com", Rol.SUPERVISOR, "Eva", "Inactivo", "91000022");
        inactivo.setActivo(false);
        Usuario pendiente = crearUsuario("estados.pendiente@example.com", Rol.SUPERVISOR, "Eva", "Pendiente", "91000023");
        pendiente.setDebeCambiarClave(true);
        Usuario bloqueado = crearUsuario("estados.bloqueado@example.com", Rol.SUPERVISOR, "Eva", "Bloqueado", "91000024");
        correoBloqueado = bloqueado.getCorreo();
        for (int i = 0; i < 5; i++) {
            almacenIntentosFallidos.registrarFallo(correoBloqueado);
        }
        usuarioRepository.saveAll(java.util.List.of(inactivo, pendiente));

        buscarPorEstado(admin, "INACTIVA", "Inactivo");
        buscarPorEstado(admin, "CLAVE_PENDIENTE", "Pendiente");
        buscarPorEstado(admin, "BLOQUEADA", "Bloqueado");
        // La cuenta activa del propio admin aparece como ACTIVA; ni la bloqueada ni la pendiente.
        mockMvc.perform(get("/api/usuarios/buscar").param("q", "eva").param("estado", "ACTIVA")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].apellidos").value("Estados"));
    }

    @Test
    void buscar_conEstadoOTamanoInvalido_devuelve400() throws Exception {
        Usuario admin = crearUsuario("invalido.admin@example.com", Rol.RRHH_ADMIN, "Ines", "Invalida", "91000031");

        mockMvc.perform(get("/api/usuarios/buscar").param("estado", "SUSPENDIDA").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/usuarios/buscar").param("tamano", "500").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscarEHistorial_sonSoloDelAdministrador() throws Exception {
        Usuario supervisor = crearUsuario("permiso.supervisor@example.com", Rol.SUPERVISOR, "Sara", "Permiso", "91000041");

        mockMvc.perform(get("/api/usuarios/buscar").header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios/" + supervisor.getId() + "/historial").header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/usuarios/" + supervisor.getId()).header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/usuarios/" + supervisor.getId() + "/desbloquear").header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarConMotivo_yReactivar_quedaEnElUsuarioYEnElHistorial() throws Exception {
        Usuario admin = crearUsuario("baja.admin@example.com", Rol.RRHH_ADMIN, "Ada", "Baja", "91000051");
        Usuario objetivo = crearUsuario("baja.objetivo@example.com", Rol.SUPERVISOR, "Beto", "Objetivo", "91000052");
        String token = bearer(admin);

        mockMvc.perform(post("/api/usuarios/" + objetivo.getId() + "/desactivar")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Fin de contrato\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCuenta").value("INACTIVA"))
                .andExpect(jsonPath("$.motivoBaja").value("Fin de contrato"))
                .andExpect(jsonPath("$.desactivadoEn").isNotEmpty());

        mockMvc.perform(post("/api/usuarios/" + objetivo.getId() + "/activar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCuenta").value("ACTIVA"))
                .andExpect(jsonPath("$.motivoBaja").doesNotExist())
                .andExpect(jsonPath("$.desactivadoEn").doesNotExist());

        mockMvc.perform(get("/api/usuarios/" + objetivo.getId() + "/historial").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accion").value("MODIFICACION"))
                .andExpect(jsonPath("$[0].actorId").value(admin.getId()))
                .andExpect(jsonPath("$[0].valoresNuevos.activo").value(true))
                .andExpect(jsonPath("$[1].valoresNuevos.motivoBaja").value("Fin de contrato"));
    }

    @Test
    void desactivarSinCuerpo_funcionaIgualQueAntes() throws Exception {
        Usuario admin = crearUsuario("sincuerpo.admin@example.com", Rol.RRHH_ADMIN, "Ada", "Sincuerpo", "91000061");
        Usuario objetivo = crearUsuario("sincuerpo.objetivo@example.com", Rol.SUPERVISOR, "Beto", "Sincuerpo", "91000062");

        mockMvc.perform(post("/api/usuarios/" + objetivo.getId() + "/desactivar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.motivoBaja").doesNotExist());
    }

    @Test
    void desbloquear_levantaElBloqueoPorIntentosFallidos() throws Exception {
        Usuario admin = crearUsuario("desbloqueo.admin@example.com", Rol.RRHH_ADMIN, "Ada", "Desbloqueo", "91000071");
        Usuario objetivo = crearUsuario("desbloqueo.objetivo@example.com", Rol.SUPERVISOR, "Beto", "Bloqueado", "91000072");
        correoBloqueado = objetivo.getCorreo();
        for (int i = 0; i < 5; i++) {
            almacenIntentosFallidos.registrarFallo(correoBloqueado);
        }

        mockMvc.perform(get("/api/usuarios/" + objetivo.getId()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.estadoCuenta").value("BLOQUEADA"));

        mockMvc.perform(post("/api/usuarios/" + objetivo.getId() + "/desbloquear").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCuenta").value("ACTIVA"));
    }

    @Test
    void eliminar_usuarioSinHistorial_loBorraYQuedaEnBitacora() throws Exception {
        Usuario admin = crearUsuario("borrar.admin@example.com", Rol.RRHH_ADMIN, "Ada", "Borrar", "91000081");
        Usuario objetivo = crearUsuario("borrar.objetivo@example.com", Rol.COLABORADOR, "Beto", "Borrable", "91000082");
        Long id = objetivo.getId();

        mockMvc.perform(delete("/api/usuarios/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/usuarios/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminar_usuarioConHistorial_respondeConflictoYSigueExistiendo() throws Exception {
        Usuario adminConHistorial = crearUsuario("historial.admin1@example.com", Rol.RRHH_ADMIN, "Ada", "Historial", "91000091");
        Usuario otroAdmin = crearUsuario("historial.admin2@example.com", Rol.RRHH_ADMIN, "Otto", "Historial", "91000092");

        // adminConHistorial crea un usuario: queda como actor en la bitácora, y eso ya es historial.
        mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(adminConHistorial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombres":"Carla","apellidos":"Creada","tipoDocumento":"DNI","numeroDocumento":"91000093",
                             "correo":"historial.creada@example.com","rol":"COLABORADOR"}
                            """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/usuarios/" + adminConHistorial.getId()).header("Authorization", bearer(otroAdmin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("desactívelo")));

        mockMvc.perform(get("/api/usuarios/" + adminConHistorial.getId()).header("Authorization", bearer(otroAdmin)))
                .andExpect(status().isOk());
    }

    @Test
    void eliminar_laPropiaCuenta_respondeConflicto() throws Exception {
        Usuario admin = crearUsuario("propia.admin1@example.com", Rol.RRHH_ADMIN, "Ada", "Propia", "70000101");
        crearUsuario("propia.admin2@example.com", Rol.RRHH_ADMIN, "Otto", "Propia", "70000102");

        mockMvc.perform(delete("/api/usuarios/" + admin.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void editar_correoConMayusculas_seGuardaEnMinusculasYNoChocaConSiMismo() throws Exception {
        Usuario admin = crearUsuario("mayus.admin@example.com", Rol.RRHH_ADMIN, "Ada", "Mayus", "70000111");
        Usuario objetivo = crearUsuario("mayus.objetivo@example.com", Rol.SUPERVISOR, "Beto", "Mayus", "70000112");

        mockMvc.perform(put("/api/usuarios/" + objetivo.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombres":"Beto","apellidos":"Mayus","tipoDocumento":"DNI","numeroDocumento":"70000112",
                             "correo":"MAYUS.Objetivo@Example.com","rol":"SUPERVISOR"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("mayus.objetivo@example.com"));

        mockMvc.perform(put("/api/usuarios/" + objetivo.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"nombres":"Beto","apellidos":"Mayus","tipoDocumento":"DNI","numeroDocumento":"70000112",
                             "correo":"MAYUS.ADMIN@example.com","rol":"SUPERVISOR"}
                            """))
                .andExpect(status().isConflict());
    }

    private void buscarPorEstado(Usuario admin, String estado, String apellidoEsperado) throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar").param("q", "eva").param("estado", estado)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].apellidos").value(apellidoEsperado))
                .andExpect(jsonPath("$.contenido[0].estadoCuenta").value(estado));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Usuario crearUsuario(String correo, String nombreRol, String nombres, String apellidos, String documento) {
        Rol rol = rolRepository.findByNombre(nombreRol).orElseThrow();
        boolean esColaborador = Rol.COLABORADOR.equals(nombreRol);
        return usuarioRepository.save(Usuario.builder()
                .nombres(nombres).apellidos(apellidos)
                .tipoDocumento("DNI").numeroDocumento(documento)
                .correo(correo)
                .passwordHash(esColaborador ? null : CLAVE_FICTICIA)
                .rol(rol)
                .activo(true)
                .build());
    }
}
