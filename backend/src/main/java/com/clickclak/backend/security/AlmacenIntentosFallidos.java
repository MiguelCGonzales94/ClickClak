package com.clickclak.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Frena la fuerza bruta contra una cuenta: tras {@value #MAXIMO_FALLOS} contraseñas incorrectas
 * dentro de {@code VENTANA}, la cuenta queda bloqueada durante {@code VENTANA}. La clave es el
 * correo que se envió, exista o no, para que el bloqueo no delate qué cuentas existen.
 *
 * <p>Límites conocidos, aceptados para esta versión:
 * <ul>
 *   <li>Vive en memoria: un reinicio del backend lo borra, y no se comparte entre instancias
 *       (hay una sola). Igual que {@link AlmacenTokensRevocados}.</li>
 *   <li>Quien conozca un correo puede bloquearlo 15 minutos enviando intentos fallidos. Es el
 *       costo de bloquear por cuenta; el límite por IP del proxy acota cuánto puede repetirlo.</li>
 *   <li>Acotado a {@value #MAXIMO_ENTRADAS} cuentas rastreadas para que un atacante no agote la
 *       memoria con correos inventados; el límite por IP del proxy cubre ese caso.</li>
 * </ul>
 */
@Component
public class AlmacenIntentosFallidos {

    static final int MAXIMO_FALLOS = 5;
    static final int MAXIMO_ENTRADAS = 10_000;
    private static final Duration VENTANA = Duration.ofMinutes(15);

    private record Registro(int fallos, Instant inicioVentana, Instant bloqueadoHasta) {
        boolean vencido(Instant ahora) {
            return (bloqueadoHasta == null || !bloqueadoHasta.isAfter(ahora))
                    && !inicioVentana.plus(VENTANA).isAfter(ahora);
        }
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Clock reloj;

    public AlmacenIntentosFallidos() {
        this(Clock.systemUTC());
    }

    public AlmacenIntentosFallidos(Clock reloj) {
        this.reloj = reloj;
    }

    /** Tiempo que falta para que la cuenta se desbloquee, o vacío si no está bloqueada. */
    public Optional<Duration> tiempoRestanteDeBloqueo(String correo) {
        Instant ahora = reloj.instant();
        Registro registro = registros.get(normalizar(correo));
        if (registro == null || registro.bloqueadoHasta() == null || !registro.bloqueadoHasta().isAfter(ahora)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(ahora, registro.bloqueadoHasta()));
    }

    public void registrarFallo(String correo) {
        Instant ahora = reloj.instant();
        String clave = normalizar(correo);
        if (registros.size() >= MAXIMO_ENTRADAS && !registros.containsKey(clave)) {
            registros.values().removeIf(registro -> registro.vencido(ahora));
            if (registros.size() >= MAXIMO_ENTRADAS) {
                return;
            }
        }
        registros.compute(clave, (k, actual) -> {
            if (actual == null || actual.vencido(ahora)) {
                return new Registro(1, ahora, null);
            }
            int fallos = actual.fallos() + 1;
            Instant bloqueadoHasta = fallos >= MAXIMO_FALLOS ? ahora.plus(VENTANA) : actual.bloqueadoHasta();
            return new Registro(fallos, actual.inicioVentana(), bloqueadoHasta);
        });
    }

    /** Un acceso correcto borra el historial de fallos de esa cuenta. */
    public void limpiar(String correo) {
        registros.remove(normalizar(correo));
    }

    private String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
