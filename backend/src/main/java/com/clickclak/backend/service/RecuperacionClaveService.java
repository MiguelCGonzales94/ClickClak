package com.clickclak.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.MensajeResponse;
import com.clickclak.backend.dto.RestablecerClaveRequest;
import com.clickclak.backend.dto.SolicitarRecuperacionRequest;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenTokensRecuperacion;
import com.clickclak.backend.security.PoliticaContrasenas;

/**
 * HU03: recuperación de acceso para SUPERVISOR/RRHH_ADMIN (los únicos roles con
 * contraseña — un COLABORADOR "recupera" su acceso re-enrolando su dispositivo, ver HU05).
 */
@Service
public class RecuperacionClaveService {

    private final UsuarioRepository usuarioRepository;
    private final AlmacenTokensRecuperacion almacenTokens;
    private final NotificadorRecuperacion notificador;
    private final PasswordEncoder passwordEncoder;

    public RecuperacionClaveService(
            UsuarioRepository usuarioRepository,
            AlmacenTokensRecuperacion almacenTokens,
            NotificadorRecuperacion notificador,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.almacenTokens = almacenTokens;
        this.notificador = notificador;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * El mensaje de respuesta es siempre el mismo, exista o no el correo — mismo principio
     * que {@link com.clickclak.backend.exception.CredencialesInvalidasException}: no revelar
     * qué correos están registrados.
     */
    @Transactional(readOnly = true)
    public MensajeResponse solicitar(SolicitarRecuperacionRequest solicitud) {
        usuarioRepository.findByCorreo(solicitud.correo())
                .filter(Usuario::isActivo)
                .filter(usuario -> usuario.getPasswordHash() != null)
                .ifPresent(usuario -> {
                    String token = almacenTokens.generar(usuario.getId());
                    notificador.enviarEnlaceRecuperacion(usuario.getCorreo(), token);
                });

        return new MensajeResponse(
                "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.");
    }

    @Transactional
    public MensajeResponse restablecer(RestablecerClaveRequest solicitud) {
        Long usuarioId = almacenTokens.consumir(solicitud.token())
                .orElseThrow(() -> new SolicitudInvalidaException("El enlace de recuperación no es válido o ya expiró"));

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new SolicitudInvalidaException("El enlace de recuperación no es válido o ya expiró"));

        if (Rol.COLABORADOR.equals(usuario.getRol().getNombre())) {
            throw new SolicitudInvalidaException("Este usuario no usa contraseña: su acceso es por WebAuthn");
        }

        PoliticaContrasenas.validar(solicitud.nuevaPassword());
        usuario.setPasswordHash(passwordEncoder.encode(solicitud.nuevaPassword()));
        // Eligió su propia clave: ya no hay clave temporal pendiente de cambio (HU04).
        usuario.setDebeCambiarClave(false);
        usuarioRepository.save(usuario);

        return new MensajeResponse("Contraseña actualizada correctamente. Ya puedes iniciar sesión.");
    }
}
