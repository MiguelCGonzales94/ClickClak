package com.clickclak.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Retiene el desafío criptográfico entre "iniciar" y "finalizar" de cada ceremonia
 * WebAuthn. No hay sesión de servidor (API stateless, ver SecurityConfig), así que este
 * almacén en memoria hace ese papel: de un solo uso y con expiración corta, porque el
 * desafío solo tiene sentido durante los segundos que dura la interacción biométrica.
 *
 * <p>Alcance suficiente para una sola instancia de backend (el previsto académicamente);
 * una instalación con varias réplicas necesitaría moverlo a un almacén compartido
 * (Redis, por ejemplo) para que "iniciar" y "finalizar" puedan caer en instancias distintas.
 */
@Component
public class AlmacenDesafiosWebAuthn {

    private static final Duration VIGENCIA_POR_DEFECTO = Duration.ofMinutes(5);

    private record Entrada(Object valor, Instant expiraEn) {
    }

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final Duration vigencia;

    public AlmacenDesafiosWebAuthn() {
        this(VIGENCIA_POR_DEFECTO);
    }

    /** Vigencia configurable solo para poder probar la expiración sin esperar minutos reales. */
    AlmacenDesafiosWebAuthn(Duration vigencia) {
        this.vigencia = vigencia;
    }

    public String guardar(Object valor) {
        String id = UUID.randomUUID().toString();
        entradas.put(id, new Entrada(valor, Instant.now().plus(vigencia)));
        return id;
    }

    /** Un solo uso: se retira al consultarlo, exista o no, para impedir reintentos del mismo desafío. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> recuperar(String id, Class<T> tipo) {
        Entrada entrada = entradas.remove(id);
        if (entrada == null || entrada.expiraEn().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return tipo.isInstance(entrada.valor()) ? Optional.of((T) entrada.valor()) : Optional.empty();
    }
}
