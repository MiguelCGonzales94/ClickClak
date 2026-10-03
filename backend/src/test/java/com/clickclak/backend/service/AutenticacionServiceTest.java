package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.clickclak.backend.dto.LoginRequest;
import com.clickclak.backend.exception.CredencialesInvalidasException;
import com.clickclak.backend.exception.DemasiadosIntentosException;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenIntentosFallidos;
import com.clickclak.backend.security.AlmacenTokensRevocados;
import com.clickclak.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private JwtService jwtService;
    private AlmacenTokensRevocados almacenTokensRevocados;
    private AlmacenIntentosFallidos almacenIntentosFallidos;
    private AutenticacionService autenticacionService;

    @BeforeEach
    void configurar() {
        jwtService = new JwtService("clave-de-prueba-suficientemente-larga-para-hmac-sha256", 60);
        almacenTokensRevocados = new AlmacenTokensRevocados();
        almacenIntentosFallidos = new AlmacenIntentosFallidos();
        // El servicio calcula en su constructor el hash ficticio con el que iguala los tiempos de respuesta.
        when(passwordEncoder.encode(any())).thenReturn("hash-ficticio");
        autenticacionService = new AutenticacionService(
                usuarioRepository, passwordEncoder, jwtService, almacenTokensRevocados, almacenIntentosFallidos);
    }

    @Test
    void credencialesCorrectas_devuelveTokenConDatosDelUsuario() {
        Usuario usuario = usuarioConPassword("hash-guardado");
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave123", "hash-guardado")).thenReturn(true);

        var respuesta = autenticacionService.autenticar(new LoginRequest("ana@example.com", "clave123"));

        assertThat(respuesta.token()).isNotBlank();
        assertThat(respuesta.rol()).isEqualTo(Rol.SUPERVISOR);
    }

    @Test
    void contrasenaIncorrecta_lanzaCredencialesInvalidas() {
        Usuario usuario = usuarioConPassword("hash-guardado");
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash-guardado")).thenReturn(false);

        assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("ana@example.com", "incorrecta")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void correoInexistente_lanzaCredencialesInvalidas() {
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("nadie@example.com", "clave123")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void colaboradorSinPassword_nuncaPuedeUsarLoginClasico() {
        Usuario colaborador = Usuario.builder()
                .id(5L).correo("colaborador@example.com").passwordHash(null).activo(true)
                .rol(Rol.builder().nombre(Rol.COLABORADOR).build())
                .build();
        when(usuarioRepository.findByCorreo("colaborador@example.com")).thenReturn(Optional.of(colaborador));

        assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("colaborador@example.com", "cualquiera")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void cerrarSesion_dejaElTokenRevocado() {
        Usuario usuario = usuarioConPassword("hash-guardado");
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave123", "hash-guardado")).thenReturn(true);
        String token = autenticacionService.autenticar(new LoginRequest("ana@example.com", "clave123")).token();

        assertThat(almacenTokensRevocados.estaRevocado(token)).isFalse();
        autenticacionService.cerrarSesion(token);
        assertThat(almacenTokensRevocados.estaRevocado(token)).isTrue();
    }

    @Test
    void cerrarSesion_conTokenInvalido_noLanzaExcepcion() {
        autenticacionService.cerrarSesion("esto-no-es-un-jwt-valido");
    }

    // --- fuerza bruta y enumeración de cuentas -------------------------------------------------

    @Test
    void cincoFallos_bloqueanLaCuentaAunConLaClaveCorrecta() {
        Usuario usuario = usuarioConPassword("hash-guardado");
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash-guardado")).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("ana@example.com", "incorrecta")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        // Ya bloqueada: ni siquiera se evalúa la contraseña, así que acertarla no desbloquea.
        assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("ana@example.com", "clave123")))
                .isInstanceOf(DemasiadosIntentosException.class)
                .satisfies(ex -> assertThat(((DemasiadosIntentosException) ex).getSegundosRestantes()).isPositive());
    }

    @Test
    void unAccesoCorrecto_reiniciaElContadorDeFallos() {
        Usuario usuario = usuarioConPassword("hash-guardado");
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash-guardado")).thenReturn(false);
        when(passwordEncoder.matches("clave123", "hash-guardado")).thenReturn(true);

        for (int ronda = 0; ronda < 3; ronda++) {
            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("ana@example.com", "incorrecta")))
                        .isInstanceOf(CredencialesInvalidasException.class);
            }
            assertThat(autenticacionService.autenticar(new LoginRequest("ana@example.com", "clave123")).token()).isNotBlank();
        }
    }

    @Test
    void elBloqueoNoDelataSiLaCuentaExiste() {
        when(usuarioRepository.findByCorreo("fantasma@example.com")).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("fantasma@example.com", "x12345678")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("fantasma@example.com", "x12345678")))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void elBloqueoNoDistingueMayusculasNiEspacios() {
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest("Ana@Example.com", "x12345678")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest(" ana@example.com ", "x12345678")))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void sinCuentaUtil_siempreSeComparaContraElHashFicticio() {
        // Mismo trabajo de hash exista o no la cuenta: el tiempo de respuesta no permite enumerar usuarios.
        when(usuarioRepository.findByCorreo("nadie@example.com")).thenReturn(Optional.empty());
        Usuario inactivo = usuarioConPassword("hash-guardado");
        inactivo.setActivo(false);
        when(usuarioRepository.findByCorreo("inactivo@example.com")).thenReturn(Optional.of(inactivo));
        Usuario colaborador = Usuario.builder().id(5L).correo("colab@example.com").passwordHash(null).activo(true)
                .rol(Rol.builder().nombre(Rol.COLABORADOR).build()).build();
        when(usuarioRepository.findByCorreo("colab@example.com")).thenReturn(Optional.of(colaborador));

        for (String correo : new String[] {"nadie@example.com", "inactivo@example.com", "colab@example.com"}) {
            assertThatThrownBy(() -> autenticacionService.autenticar(new LoginRequest(correo, "clave123")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        verify(passwordEncoder, org.mockito.Mockito.times(3)).matches("clave123", "hash-ficticio");
    }

    private Usuario usuarioConPassword(String hash) {
        return Usuario.builder()
                .id(1L).correo("ana@example.com").passwordHash(hash).activo(true)
                .nombres("Ana").apellidos("Pérez")
                .rol(Rol.builder().nombre(Rol.SUPERVISOR).build())
                .build();
    }
}
