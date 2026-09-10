package com.clickclak.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * HU03: "el enlace o código tiene vigencia limitada". Guarda, contra un token opaco de un
 * solo uso, el id del usuario que solicitó recuperar su acceso. En memoria — mismo alcance
 * y misma limitación que {@link AlmacenDesafiosWebAuthn} (una sola instancia de backend).
 */
@Component
public class AlmacenTokensRecuperacion {

    private static final Duration VIGENCIA = Duration.ofMinutes(30);

    private record Entrada(Long usuarioId, Instant expiraEn) {
    }

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();

    public String generar(Long usuarioId) {
        entradas.entrySet().removeIf(entrada -> entrada.getValue().expiraEn().isBefore(Instant.now()));
        String token = UUID.randomUUID().toString();
        entradas.put(token, new Entrada(usuarioId, Instant.now().plus(VIGENCIA)));
        return token;
    }

    /** Un solo uso: se retira al consultarlo, exista o no, para que el enlace no sirva dos veces. */
    public Optional<Long> consumir(String token) {
        Entrada entrada = entradas.remove(token);
        if (entrada == null || entrada.expiraEn().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entrada.usuarioId());
    }
}
