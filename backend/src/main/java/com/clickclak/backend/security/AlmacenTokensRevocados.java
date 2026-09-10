package com.clickclak.backend.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * HU02: "el cierre invalida la sesión activa". Los JWT de este sistema son *stateless* por
 * diseño (ver {@link JwtAuthenticationFilter}), así que cerrar sesión no "borra" nada en el
 * servidor por sí solo — el token seguiría siendo válido hasta su expiración natural (hasta
 * 8h). Esta lista de revocación es lo que hace que, tras un logout explícito, ese mismo
 * token deje de servir de inmediato.
 *
 * <p>En memoria, suficiente para una sola instancia de backend (alcance académico) — una
 * instalación con varias réplicas necesitaría un almacén compartido (Redis). Se purgan las
 * entradas ya expiradas en cada revocación para no crecer sin límite.
 */
@Component
public class AlmacenTokensRevocados {

    private final Map<String, Instant> revocados = new ConcurrentHashMap<>();

    public void revocar(String token, Instant expiraEn) {
        revocados.entrySet().removeIf(entrada -> entrada.getValue().isBefore(Instant.now()));
        revocados.put(token, expiraEn);
    }

    public boolean estaRevocado(String token) {
        return revocados.containsKey(token);
    }
}
