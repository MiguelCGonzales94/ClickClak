package com.clickclak.backend.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.DireccionResponse;
import com.clickclak.backend.exception.ServicioExternoNoDisponibleException;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;

/** El servicio de geocodificación se simula: la prueba no sale a internet. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GeocodificacionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JwtService jwtService;
    @MockBean private com.clickclak.backend.service.GeocodificacionService geocodificacionService;

    @Test
    void supervisionRecibeLaDireccionConLosNombresDeCampoDelServicio() throws Exception {
        Usuario admin = crearUsuario("geo.admin@example.com", Rol.RRHH_ADMIN, "95000001");
        when(geocodificacionService.direccionDe(-12.0223, -77.0979)).thenReturn(
                new DireccionResponse("Avenida El Olivar, Lima", Map.of("road", "Avenida El Olivar"), null));

        mockMvc.perform(get("/api/geocodificacion/inversa").param("latitud", "-12.0223").param("longitud", "-77.0979")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("Avenida El Olivar, Lima"))
                .andExpect(jsonPath("$.address.road").value("Avenida El Olivar"));
    }

    @Test
    void siElServicioExternoNoResponde_elPanelRecibe502ConUnMensajeSinDetallesInternos() throws Exception {
        Usuario admin = crearUsuario("geo.admin2@example.com", Rol.RRHH_ADMIN, "95000002");
        when(geocodificacionService.direccionDe(anyDouble(), anyDouble()))
                .thenThrow(new ServicioExternoNoDisponibleException("No se pudo consultar la dirección del punto", new RuntimeException("conexión rechazada")));

        mockMvc.perform(get("/api/geocodificacion/inversa").param("latitud", "-12").param("longitud", "-77")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("No se pudo consultar la dirección del punto"));
    }

    @Test
    void soloSupervisionConsulta_unColaboradorOUnAnonimoNo() throws Exception {
        Usuario colaborador = crearUsuario("geo.col@example.com", Rol.COLABORADOR, "95000003");

        mockMvc.perform(get("/api/geocodificacion/inversa").param("latitud", "-12").param("longitud", "-77")
                        .header("Authorization", bearer(colaborador)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/geocodificacion/inversa").param("latitud", "-12").param("longitud", "-77"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private Usuario crearUsuario(String correo, String nombreRol, String documento) {
        return usuarioRepository.save(Usuario.builder()
                .nombres("Persona").apellidos(correo).tipoDocumento("DNI").numeroDocumento(documento).correo(correo)
                .passwordHash(Rol.COLABORADOR.equals(nombreRol) ? null : "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvwxyzABCDE")
                .rol(rolRepository.findByNombre(nombreRol).orElseThrow()).activo(true).build());
    }
}
