package com.clickclak.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DispositivoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DispositivoRepository dispositivoRepository;
    @Autowired private JwtService jwtService;

    @Test
    void listarMios_devuelveSoloDispositivosActivosDelUsuarioAutenticado() throws Exception {
        Usuario tecnico = crearTecnico("dispositivos1@example.com");
        Usuario otroTecnico = crearTecnico("dispositivos2@example.com");
        dispositivoRepository.save(dispositivo(tecnico, "Android principal", true));
        dispositivoRepository.save(dispositivo(tecnico, "Equipo revocado", false));
        dispositivoRepository.save(dispositivo(otroTecnico, "Equipo de otra cuenta", true));

        String token = "Bearer " + jwtService.generarToken(tecnico);

        mockMvc.perform(get("/api/dispositivos/mios").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombreDispositivo").value("Android principal"))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].credentialId").doesNotExist())
                .andExpect(jsonPath("$[0].clavePublica").doesNotExist());
    }

    @Test
    void listarMios_sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/dispositivos/mios"))
                .andExpect(status().isUnauthorized());
    }

    private Usuario crearTecnico(String correo) {
        Rol rolColaborador = rolRepository.findByNombre(Rol.COLABORADOR).orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .nombres("Ana").apellidos("Pérez")
                .tipoDocumento("DNI").numeroDocumento(Integer.toString(correo.hashCode()))
                .correo(correo)
                .rol(rolColaborador)
                .activo(true)
                .build());
    }

    private Dispositivo dispositivo(Usuario usuario, String nombre, boolean activo) {
        return Dispositivo.builder()
                .usuario(usuario)
                .credentialId("cred-" + usuario.getCorreo() + "-" + nombre)
                .clavePublica("clave-publica-demo")
                .nombreDispositivo(nombre)
                .activo(activo)
                .build();
    }
}
