package com.clickclak.backend.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clickclak.backend.security.RepositorioCredencialesWebAuthn;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;

/**
 * Relying Party WebAuthn (HU05): valida biometría delegada al sistema operativo del
 * dispositivo sin que la huella o el rostro viajen nunca al servidor — solo se firma un
 * desafío con una llave privada que nunca sale del dispositivo.
 */
@Configuration
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(
            @Value("${clickclak.seguridad.webauthn.rp-id}") String rpId,
            @Value("${clickclak.seguridad.webauthn.rp-name}") String rpName,
            @Value("${clickclak.seguridad.webauthn.origenes}") String origenes,
            RepositorioCredencialesWebAuthn repositorioCredenciales) {

        RelyingPartyIdentity identidad = RelyingPartyIdentity.builder()
                .id(rpId)
                .name(rpName)
                .build();

        Set<String> setOrigenes = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .collect(Collectors.toSet());

        return RelyingParty.builder()
                .identity(identidad)
                .credentialRepository(repositorioCredenciales)
                .origins(setOrigenes)
                .build();
    }
}
