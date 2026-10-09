package com.clickclak.backend.service;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.CambiarClaveRequest;
import com.clickclak.backend.dto.MensajeResponse;
import com.clickclak.backend.exception.DemasiadosIntentosException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenIntentosFallidos;
import com.clickclak.backend.security.PoliticaContrasenas;

/**
 * HU04: cambio de la propia contraseña, voluntario o forzado tras un restablecimiento del
 * administrador. Exige la contraseña actual aunque haya sesión: un token robado no debería
 * bastar para fijar una clave nueva. Los intentos fallidos cuentan para el mismo bloqueo del
 * login, para que esta pantalla no sirva para adivinar la clave actual.
 */
@Service
public class CambioClaveService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AlmacenIntentosFallidos almacenIntentosFallidos;
    private final AutenticacionService autenticacionService;
    private final AuditoriaService auditoriaService;

    public CambioClaveService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AlmacenIntentosFallidos almacenIntentosFallidos,
            AutenticacionService autenticacionService,
            AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.almacenIntentosFallidos = almacenIntentosFallidos;
        this.autenticacionService = autenticacionService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Revoca el token con que se hizo la llamada: el usuario vuelve a iniciar sesión con la clave
     * nueva. {@code token} es el JWT sin el prefijo "Bearer ".
     */
    @Transactional
    public MensajeResponse cambiarClave(Long usuarioId, String token, CambiarClaveRequest solicitud) {
        Usuario usuario = usuarioRepository.findConRolById(usuarioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));

        almacenIntentosFallidos.tiempoRestanteDeBloqueo(usuario.getCorreo()).ifPresent(restante -> {
            throw new DemasiadosIntentosException(Math.max(1, restante.toSeconds()));
        });

        if (Rol.COLABORADOR.equals(usuario.getRol().getNombre()) || usuario.getPasswordHash() == null) {
            throw new SolicitudInvalidaException("Este usuario no usa contraseña: su acceso es por WebAuthn");
        }
        if (!passwordEncoder.matches(solicitud.claveActual(), usuario.getPasswordHash())) {
            almacenIntentosFallidos.registrarFallo(usuario.getCorreo());
            throw new SolicitudInvalidaException("La contraseña actual no es correcta");
        }
        PoliticaContrasenas.validar(solicitud.claveNueva());
        if (passwordEncoder.matches(solicitud.claveNueva(), usuario.getPasswordHash())) {
            throw new SolicitudInvalidaException("La contraseña nueva debe ser distinta de la actual");
        }

        boolean eraTemporal = usuario.isDebeCambiarClave();
        usuario.setPasswordHash(passwordEncoder.encode(solicitud.claveNueva()));
        usuario.setDebeCambiarClave(false);
        usuarioRepository.save(usuario);
        almacenIntentosFallidos.limpiar(usuario.getCorreo());

        auditoriaService.registrar(usuario, "usuario", usuario.getId(), AccionAuditoria.MODIFICACION,
                Map.of("debeCambiarClave", eraTemporal),
                Map.of("debeCambiarClave", false, "clave", "cambiada por el propio usuario"));
        autenticacionService.cerrarSesion(token);
        return new MensajeResponse("Contraseña actualizada. Inicia sesión de nuevo con la nueva contraseña.");
    }
}
