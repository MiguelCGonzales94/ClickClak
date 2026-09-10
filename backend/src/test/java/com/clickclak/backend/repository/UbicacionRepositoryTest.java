package com.clickclak.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Proyecto;
import com.clickclak.backend.model.Ubicacion;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Verifica contra el Postgres+PostGIS real que la consulta nativa de distancia
 * (la pieza más frágil del motor de validación contextual) funciona correctamente.
 */
@SpringBootTest
@Transactional
class UbicacionRepositoryTest {

    private static final GeometryFactory FABRICA = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private UbicacionRepository ubicacionRepository;

    @Test
    void calculaDistanciaRealConPostgis() {
        Proyecto proyecto = proyectoRepository.save(Proyecto.builder()
                .nombre("Proyecto Prueba")
                .cliente("Cliente Prueba")
                .fechaInicio(java.time.LocalDate.now())
                .build());

        Ubicacion ubicacion = ubicacionRepository.save(Ubicacion.builder()
                .proyecto(proyecto)
                .nombre("Sede Prueba")
                .geom(FABRICA.createPoint(new Coordinate(-77.0428, -12.0464)))
                .radioToleranciaMetros(150)
                .build());

        // Punto muy cercano (misma sede, ligera variación de coordenadas).
        double distanciaCorta = ubicacionRepository.calcularDistanciaMetros(
                ubicacion.getId(), -12.0466, -77.0430);
        assertThat(distanciaCorta).isLessThan(50);

        // Punto claramente lejano (~5 km aprox. en Lima).
        double distanciaLarga = ubicacionRepository.calcularDistanciaMetros(
                ubicacion.getId(), -12.09, -77.02);
        assertThat(distanciaLarga).isGreaterThan(4000);
    }
}
