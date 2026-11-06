package com.clickclak.backend.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.clickclak.backend.service.GeocodificacionService;

/**
 * Cliente de la geocodificación inversa. La URL se puede cambiar por entorno (un Nominatim propio, por
 * ejemplo) con {@code clickclak.geocodificacion.url}. El servicio público pide identificarse con un
 * {@code User-Agent} propio de la aplicación y no más de una consulta por segundo.
 */
@Configuration
public class GeocodificacionConfig {

    @Bean
    GeocodificacionService geocodificacionService(
            @Value("${clickclak.geocodificacion.url:https://nominatim.openstreetmap.org}") String url,
            @Value("${clickclak.geocodificacion.separacion-ms:1100}") long separacionMs) {
        var fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        RestClient cliente = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeader("User-Agent", "ClickClak-Panel/1.0")
                .defaultHeader("Accept", "application/json")
                .build();
        return new GeocodificacionService(cliente, separacionMs);
    }
}
