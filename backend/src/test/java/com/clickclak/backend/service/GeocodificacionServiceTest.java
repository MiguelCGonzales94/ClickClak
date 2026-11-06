package com.clickclak.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.clickclak.backend.dto.DireccionResponse;
import com.clickclak.backend.exception.ServicioExternoNoDisponibleException;
import com.clickclak.backend.exception.SolicitudInvalidaException;

/** Con un servidor simulado: ninguna prueba toca internet ni depende de que OpenStreetMap esté arriba. */
class GeocodificacionServiceTest {

    private static final String RESPUESTA = """
        {"place_id":1,"display_name":"Avenida El Olivar, Los Olivos, Lima, Perú",
         "address":{"road":"Avenida El Olivar","suburb":"Urb. Los Pinos","city_district":"Los Olivos","country":"Perú"},
         "boundingbox":["-12.02","-12.01","-77.10","-77.09"]}
        """;

    private MockRestServiceServer servidor;
    private GeocodificacionService servicio;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://geo.test");
        servidor = MockRestServiceServer.bindTo(constructor).build();
        servicio = new GeocodificacionService(constructor.build(), 0);
    }

    @Test
    void devuelveLaDireccionDelPuntoPidiendoEspanolYElPuntoExacto() {
        servidor.expect(once(), requestTo(startsWith("http://geo.test/reverse")))
                .andExpect(queryParam("lat", "-12.022366"))
                .andExpect(queryParam("lon", "-77.097942"))
                .andExpect(queryParam("accept-language", "es"))
                .andExpect(queryParam("format", "jsonv2"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        DireccionResponse direccion = servicio.direccionDe(-12.022366, -77.097942);

        assertThat(direccion.displayName()).startsWith("Avenida El Olivar");
        assertThat(direccion.address()).containsEntry("road", "Avenida El Olivar").containsEntry("city_district", "Los Olivos");
        servidor.verify();
    }

    @Test
    void unPuntoApenasMovidoSeResuelveDesdeLaMemoriaSinVolverAConsultar() {
        servidor.expect(once(), requestTo(startsWith("http://geo.test/reverse")))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        servicio.direccionDe(-12.022366, -77.097942);
        DireccionResponse repetida = servicio.direccionDe(-12.022371, -77.097938); // a ~1 m

        assertThat(repetida.displayName()).startsWith("Avenida El Olivar");
        servidor.verify(); // una sola consulta al servicio externo
    }

    @Test
    void unPuntoDistintoSiSeConsulta() {
        servidor.expect(requestTo(startsWith("http://geo.test/reverse"))).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(startsWith("http://geo.test/reverse"))).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        servicio.direccionDe(-12.0223, -77.0979);
        servicio.direccionDe(-12.0500, -77.0979);

        servidor.verify();
    }

    @Test
    void sinDireccionParaElPuntoDevuelveElErrorDelServicioSinFallar() {
        servidor.expect(requestTo(startsWith("http://geo.test/reverse")))
                .andRespond(withSuccess("{\"error\":\"Unable to geocode\"}", MediaType.APPLICATION_JSON));

        DireccionResponse direccion = servicio.direccionDe(0.0, 0.0);

        assertThat(direccion.error()).isEqualTo("Unable to geocode");
        assertThat(direccion.address()).isNull();
    }

    @Test
    void siElServicioExternoFallaSeAvisaYNoSeGuardaElFallo() {
        servidor.expect(requestTo(startsWith("http://geo.test/reverse"))).andRespond(withServerError());
        servidor.expect(requestTo(startsWith("http://geo.test/reverse"))).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> servicio.direccionDe(-12.0223, -77.0979))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
        // El fallo no se recuerda: el siguiente intento vuelve a consultar y esta vez responde.
        assertThat(servicio.direccionDe(-12.0223, -77.0979).displayName()).startsWith("Avenida El Olivar");
        servidor.verify();
    }

    @Test
    void rechazaCoordenadasFueraDeRangoSinConsultar() {
        assertThatThrownBy(() -> servicio.direccionDe(91, 0)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.direccionDe(0, -181)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.direccionDe(Double.NaN, 0)).isInstanceOf(SolicitudInvalidaException.class);
        servidor.verify(); // ninguna consulta esperada ni hecha
    }
}
