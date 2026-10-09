package com.clickclak.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.FinalizarAutenticacionWebAuthnRequest;
import com.clickclak.backend.dto.FinalizarRegistroWebAuthnRequest;
import com.clickclak.backend.dto.IniciarAutenticacionWebAuthnRequest;
import com.clickclak.backend.dto.LoginResponse;
import com.clickclak.backend.dto.OpcionesWebAuthnResponse;
import com.clickclak.backend.exception.CredencialesInvalidasException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenDesafiosWebAuthn;
import com.clickclak.backend.security.JwtService;
import com.clickclak.backend.security.RepositorioCredencialesWebAuthn;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttachment;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;

/**
 * HU05: registro y verificación de credenciales WebAuthn. La huella o el rostro del
 * colaborador nunca sale de su dispositivo — aquí solo se valida la firma criptográfica
 * de un desafío contra la llave pública guardada en {@link Dispositivo}.
 */
@Service
public class WebAuthnService {

    private final RelyingParty relyingParty;
    private final UsuarioRepository usuarioRepository;
    private final DispositivoRepository dispositivoRepository;
    private final AlmacenDesafiosWebAuthn almacenDesafios;
    private final JwtService jwtService;

    public WebAuthnService(
            RelyingParty relyingParty,
            UsuarioRepository usuarioRepository,
            DispositivoRepository dispositivoRepository,
            AlmacenDesafiosWebAuthn almacenDesafios,
            JwtService jwtService) {
        this.relyingParty = relyingParty;
        this.usuarioRepository = usuarioRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.almacenDesafios = almacenDesafios;
        this.jwtService = jwtService;
    }

    /**
     * Quién puede invocar esto se controla en el controller (Supervisor/RRHH autenticado,
     * con el técnico presente) — este método no vuelve a validarlo, solo arma el desafío.
     */
    @Transactional(readOnly = true)
    public OpcionesWebAuthnResponse iniciarRegistro(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));

        UserIdentity identidadUsuario = UserIdentity.builder()
                .name(usuario.getCorreo())
                .displayName(usuario.getNombres() + " " + usuario.getApellidos())
                .id(RepositorioCredencialesWebAuthn.idUsuarioComoManejador(usuario.getId()))
                .build();

        PublicKeyCredentialCreationOptions opciones = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(identidadUsuario)
                        .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                                .residentKey(ResidentKeyRequirement.PREFERRED)
                                .userVerification(UserVerificationRequirement.REQUIRED)
                                .build())
                        .build());

        return new OpcionesWebAuthnResponse(almacenDesafios.guardar(opciones), serializar(opciones));
    }

    @Transactional
    public void finalizarRegistro(FinalizarRegistroWebAuthnRequest solicitud) {
        PublicKeyCredentialCreationOptions opciones = almacenDesafios
                .recuperar(solicitud.idSolicitud(), PublicKeyCredentialCreationOptions.class)
                .orElseThrow(() -> new SolicitudInvalidaException("La solicitud de registro expiró o no existe"));

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credencial;
        try {
            credencial = PublicKeyCredential.parseRegistrationResponseJson(solicitud.credencialJson());
        } catch (IOException ex) {
            throw new SolicitudInvalidaException("La respuesta de registro WebAuthn está malformada");
        }

        RegistrationResult resultado;
        try {
            resultado = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(opciones)
                    .response(credencial)
                    .build());
        } catch (RegistrationFailedException ex) {
            throw new SolicitudInvalidaException("No se pudo validar la credencial WebAuthn: " + ex.getMessage());
        }

        Long usuarioId = Long.valueOf(new String(opciones.getUser().getId().getBytes(), StandardCharsets.UTF_8));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));

        dispositivoRepository.save(Dispositivo.builder()
                .usuario(usuario)
                .credentialId(resultado.getKeyId().getId().getBase64Url())
                .clavePublica(resultado.getPublicKeyCose().getBase64())
                .contadorFirma(resultado.getSignatureCount())
                .nombreDispositivo(solicitud.nombreDispositivo())
                .activo(true)
                .build());
    }

    /** Público: es el propio mecanismo de login, no puede exigir un JWT previo. */
    @Transactional(readOnly = true)
    public OpcionesWebAuthnResponse iniciarAutenticacion(IniciarAutenticacionWebAuthnRequest solicitud) {
        AssertionRequest peticion = relyingParty.startAssertion(StartAssertionOptions.builder()
                .username(solicitud.correo())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build());

        return new OpcionesWebAuthnResponse(almacenDesafios.guardar(peticion), serializar(peticion));
    }

    @Transactional
    public LoginResponse finalizarAutenticacion(FinalizarAutenticacionWebAuthnRequest solicitud) {
        AssertionRequest peticion = almacenDesafios
                .recuperar(solicitud.idSolicitud(), AssertionRequest.class)
                .orElseThrow(CredencialesInvalidasException::new);

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credencial;
        try {
            credencial = PublicKeyCredential.parseAssertionResponseJson(solicitud.credencialJson());
        } catch (IOException ex) {
            throw new CredencialesInvalidasException();
        }

        AssertionResult resultado;
        try {
            resultado = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(peticion)
                    .response(credencial)
                    .build());
        } catch (AssertionFailedException ex) {
            throw new CredencialesInvalidasException();
        }

        if (!resultado.isSuccess()) {
            throw new CredencialesInvalidasException();
        }

        dispositivoRepository.findByCredentialId(resultado.getCredentialId().getBase64Url())
                .filter(Dispositivo::isActivo)
                .ifPresent(dispositivo -> {
                    dispositivo.setContadorFirma(resultado.getSignatureCount());
                    dispositivoRepository.save(dispositivo);
                });

        Usuario usuario = usuarioRepository.findByCorreo(resultado.getUsername())
                .filter(Usuario::isActivo)
                .orElseThrow(CredencialesInvalidasException::new);

        String token = jwtService.generarToken(usuario);
        return new LoginResponse(
                token, jwtService.getExpiracionMinutos(),
                usuario.getRol().getNombre(), usuario.getNombres(), usuario.getApellidos(), false);
    }

    private static String serializar(PublicKeyCredentialCreationOptions opciones) {
        try {
            return opciones.toCredentialsCreateJson();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudieron serializar las opciones de registro WebAuthn", ex);
        }
    }

    private static String serializar(AssertionRequest peticion) {
        try {
            return peticion.toCredentialsGetJson();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudieron serializar las opciones de autenticación WebAuthn", ex);
        }
    }
}
