package com.clickclak.backend.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.LoginRequest;
import com.clickclak.backend.dto.LoginResponse;
import com.clickclak.backend.dto.PerfilResponse;
import com.clickclak.backend.exception.CredencialesInvalidasException;
import com.clickclak.backend.exception.DemasiadosIntentosException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenIntentosFallidos;
import com.clickclak.backend.security.AlmacenTokensRevocados;
import com.clickclak.backend.security.JwtService;

import io.jsonwebtoken.JwtException;

/**
 * Login clásico (correo + contraseña) para supervisores y RRHH. Los colaboradores no tienen
 * {@code passwordHash} — su acceso es por WebAuthn (pendiente), así que aquí siempre caen
 * en credenciales inválidas, correctamente.
 */
@Service
public class AutenticacionService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AlmacenTokensRevocados almacenTokensRevocados;
    private final AlmacenIntentosFallidos almacenIntentosFallidos;
    /**
     * Hash de una contraseña que nadie conoce. Cuando el correo no existe, o la cuenta está
     * inactiva o no tiene contraseña, se compara contra este hash igualmente: así el login
     * tarda lo mismo exista o no la cuenta y el tiempo de respuesta no permite enumerar usuarios.
     */
    private final String hashFicticio;

    public AutenticacionService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AlmacenTokensRevocados almacenTokensRevocados,
            AlmacenIntentosFallidos almacenIntentosFallidos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.almacenTokensRevocados = almacenTokensRevocados;
        this.almacenIntentosFallidos = almacenIntentosFallidos;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * {@code @Transactional} es necesario aquí (no solo un formalismo): {@code usuario.getRol()}
     * es una asociación perezosa, y con {@code open-in-view: false} el repositorio cierra su
     * sesión de Hibernate al retornar. Sin una transacción propia que la mantenga abierta durante
     * todo el método, acceder al rol más abajo lanza {@code LazyInitializationException} —
     * bug real detectado al probar contra el servidor real, invisible en los tests porque
     * @Transactional en el propio test ya mantenía la sesión abierta.
     */
    @Transactional(readOnly = true)
    public LoginResponse autenticar(LoginRequest solicitud) {
        // El bloqueo se comprueba antes de mirar nada más: una cuenta bloqueada no se desbloquea
        // acertando la contraseña, o el bloqueo no frenaría a quien ya la adivinó por fuerza bruta.
        almacenIntentosFallidos.tiempoRestanteDeBloqueo(solicitud.correo()).ifPresent(restante -> {
            throw new DemasiadosIntentosException(Math.max(1, restante.toSeconds()));
        });

        Usuario usuario = usuarioRepository.findByCorreo(solicitud.correo())
                .filter(Usuario::isActivo)
                .orElse(null);

        boolean credencialesValidas;
        if (usuario == null || usuario.getPasswordHash() == null) {
            passwordEncoder.matches(solicitud.password(), hashFicticio);
            credencialesValidas = false;
        } else {
            credencialesValidas = passwordEncoder.matches(solicitud.password(), usuario.getPasswordHash());
        }

        if (!credencialesValidas) {
            almacenIntentosFallidos.registrarFallo(solicitud.correo());
            throw new CredencialesInvalidasException();
        }

        almacenIntentosFallidos.limpiar(solicitud.correo());
        String token = jwtService.generarToken(usuario);
        return new LoginResponse(
                token, jwtService.getExpiracionMinutos(),
                usuario.getRol().getNombre(), usuario.getNombres(), usuario.getApellidos(),
                usuario.isDebeCambiarClave());
    }

    /** Se devuelve el DTO ya ensamblado (no la entidad) por la misma razón que {@link #autenticar}. */
    @Transactional(readOnly = true)
    public PerfilResponse obtenerPerfil(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));
        return new PerfilResponse(
                usuario.getId(), usuario.getNombres(), usuario.getApellidos(),
                usuario.getCorreo(), usuario.getRol().getNombre(), usuario.isDebeCambiarClave());
    }

    /**
     * HU02: "el cierre invalida la sesión activa". Revoca el token que trae el propio
     * request de logout, para que deje de servir de inmediato — no espera a que expire solo.
     */
    public void cerrarSesion(String token) {
        try {
            var expiracion = jwtService.validar(token).getPayload().getExpiration();
            almacenTokensRevocados.revocar(token, expiracion.toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            // Token ya inválido o expirado: no hay nada que revocar, y no es un error del
            // cliente — el objetivo (que ese token no sirva) ya se cumple solo.
        }
    }
}
