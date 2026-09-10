package com.clickclak.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;

/**
 * Traduce entre el modelo de credenciales que exige la librería WebAuthn y la tabla
 * {@code dispositivo}: cada credencial registrada ES un Dispositivo autorizado (ver HU01).
 * El "userHandle" que pide la librería es el id numérico del usuario codificado como
 * texto — evita agregar una columna nueva solo para esto.
 */
@Component
public class RepositorioCredencialesWebAuthn implements CredentialRepository {

    private final UsuarioRepository usuarioRepository;
    private final DispositivoRepository dispositivoRepository;

    public RepositorioCredencialesWebAuthn(UsuarioRepository usuarioRepository, DispositivoRepository dispositivoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.dispositivoRepository = dispositivoRepository;
    }

    public static ByteArray idUsuarioComoManejador(Long usuarioId) {
        return new ByteArray(usuarioId.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Long manejadorComoIdUsuario(ByteArray manejador) {
        return Long.valueOf(new String(manejador.getBytes(), StandardCharsets.UTF_8));
    }

    private static ByteArray decodificarBase64Url(String texto) {
        try {
            return ByteArray.fromBase64Url(texto);
        } catch (Base64UrlException ex) {
            throw new IllegalStateException("credential_id corrupto en base de datos: " + texto, ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .map(usuario -> dispositivoRepository.findByUsuarioIdAndActivoTrue(usuario.getId()).stream()
                        .map(dispositivo -> PublicKeyCredentialDescriptor.builder()
                                .id(decodificarBase64Url(dispositivo.getCredentialId()))
                                .build())
                        .collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ByteArray> getUserHandleForUsername(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .filter(Usuario::isActivo)
                .map(usuario -> idUsuarioComoManejador(usuario.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getUsernameForUserHandle(ByteArray manejador) {
        try {
            return usuarioRepository.findById(manejadorComoIdUsuario(manejador))
                    .filter(Usuario::isActivo)
                    .map(Usuario::getCorreo);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegisteredCredential> lookup(ByteArray idCredencial, ByteArray manejadorUsuario) {
        return dispositivoRepository.findByCredentialId(idCredencial.getBase64Url())
                .filter(Dispositivo::isActivo)
                .filter(dispositivo -> dispositivo.getUsuario().getId().equals(manejadorComoIdUsuario(manejadorUsuario)))
                .map(RepositorioCredencialesWebAuthn::aCredencialRegistrada);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<RegisteredCredential> lookupAll(ByteArray idCredencial) {
        return dispositivoRepository.findByCredentialId(idCredencial.getBase64Url())
                .filter(Dispositivo::isActivo)
                .map(dispositivo -> Set.of(aCredencialRegistrada(dispositivo)))
                .orElseGet(Set::of);
    }

    private static RegisteredCredential aCredencialRegistrada(Dispositivo dispositivo) {
        return RegisteredCredential.builder()
                .credentialId(decodificarBase64Url(dispositivo.getCredentialId()))
                .userHandle(idUsuarioComoManejador(dispositivo.getUsuario().getId()))
                .publicKeyCose(ByteArray.fromBase64(dispositivo.getClavePublica()))
                .signatureCount(dispositivo.getContadorFirma())
                .build();
    }
}
