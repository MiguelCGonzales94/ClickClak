package com.clickclak.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.LoginRequest;
import com.clickclak.backend.dto.LoginResponse;
import com.clickclak.backend.dto.PerfilResponse;
import com.clickclak.backend.exception.CredencialesInvalidasException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;
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

    public AutenticacionService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AlmacenTokensRevocados almacenTokensRevocados) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.almacenTokensRevocados = almacenTokensRevocados;
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
        Usuario usuario = usuarioRepository.findByCorreo(solicitud.correo())
                .filter(Usuario::isActivo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (usuario.getPasswordHash() == null || !passwordEncoder.matches(solicitud.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        String token = jwtService.generarToken(usuario);
        return new LoginResponse(
                token, jwtService.getExpiracionMinutos(),
                usuario.getRol().getNombre(), usuario.getNombres(), usuario.getApellidos());
    }

    /** Se devuelve el DTO ya ensamblado (no la entidad) por la misma razón que {@link #autenticar}. */
    @Transactional(readOnly = true)
    public PerfilResponse obtenerPerfil(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));
        return new PerfilResponse(
                usuario.getId(), usuario.getNombres(), usuario.getApellidos(),
                usuario.getCorreo(), usuario.getRol().getNombre());
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
