package com.clickclak.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.clickclak.backend.dto.DireccionResponse;
import com.clickclak.backend.exception.ServicioExternoNoDisponibleException;
import com.clickclak.backend.exception.SolicitudInvalidaException;

/**
 * Dirección aproximada de un punto (geocodificación inversa) con el servicio público de OpenStreetMap.
 * La consulta la hace el servidor, no el navegador: así la política de seguridad del proxy no necesita abrir
 * conexiones a otro origen, el navegador nunca habla con un tercero y hay un único punto que cumple la política
 * de uso del servicio (identificarse, no más de una consulta por segundo, guardar lo ya consultado).
 *
 * <p>Las respuestas se recuerdan en memoria por punto (4 decimales, unos 11 m) con un tope de entradas, y las
 * consultas al servicio externo se espacian. Si el servicio no responde, se avisa con 502 y el panel sigue
 * funcionando con las coordenadas.
 */
public class GeocodificacionService {

    static final int MAXIMO_ENTRADAS_EN_CACHE = 500;

    private final RestClient cliente;
    private final long separacionEntreConsultasMs;
    private final Map<String, DireccionResponse> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DireccionResponse> eldest) {
            return size() > MAXIMO_ENTRADAS_EN_CACHE;
        }
    };
    private long ultimaConsultaMs;

    public GeocodificacionService(RestClient cliente, long separacionEntreConsultasMs) {
        this.cliente = cliente;
        this.separacionEntreConsultasMs = separacionEntreConsultasMs;
    }

    public DireccionResponse direccionDe(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new SolicitudInvalidaException("La latitud debe estar entre -90 y 90");
        }
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new SolicitudInvalidaException("La longitud debe estar entre -180 y 180");
        }
        String clave = String.format(java.util.Locale.ROOT, "%.4f,%.4f", latitud, longitud);

        synchronized (cache) {
            DireccionResponse guardada = cache.get(clave);
            if (guardada != null) {
                return guardada;
            }
        }
        DireccionResponse respuesta = consultar(latitud, longitud);
        synchronized (cache) {
            cache.put(clave, respuesta);
        }
        return respuesta;
    }

    private synchronized DireccionResponse consultar(double latitud, double longitud) {
        esperarTurno();
        try {
            DireccionResponse respuesta = cliente.get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("lat", latitud)
                            .queryParam("lon", longitud)
                            .queryParam("zoom", 18)
                            .queryParam("addressdetails", 1)
                            .queryParam("accept-language", "es")
                            .build())
                    .retrieve()
                    .body(DireccionResponse.class);
            return respuesta != null ? respuesta : new DireccionResponse(null, null, "Sin respuesta");
        } catch (RestClientException ex) {
            throw new ServicioExternoNoDisponibleException("No se pudo consultar la dirección del punto", ex);
        } finally {
            ultimaConsultaMs = System.currentTimeMillis();
        }
    }

    /** Respeta la separación mínima entre consultas al servicio externo (su política pide a lo más una por segundo). */
    private void esperarTurno() {
        long faltante = separacionEntreConsultasMs - (System.currentTimeMillis() - ultimaConsultaMs);
        if (faltante > 0) {
            try {
                Thread.sleep(faltante);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ServicioExternoNoDisponibleException("Consulta de dirección interrumpida", ex);
            }
        }
    }
}
