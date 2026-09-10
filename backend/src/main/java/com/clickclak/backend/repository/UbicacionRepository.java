package com.clickclak.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clickclak.backend.model.Ubicacion;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {
    List<Ubicacion> findByProyectoId(Long proyectoId);

    /**
     * Distancia en metros entre la ubicación y un punto dado, calculada con PostGIS
     * (ST_Distance sobre geography). Se pasan latitud/longitud como escalares en vez de
     * un objeto geometry para evitar el binding frágil de tipos espaciales en consultas nativas.
     */
    @Query(value = """
        SELECT ST_Distance(u.geom, ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)::geography)
        FROM ubicacion u
        WHERE u.id = :ubicacionId
        """, nativeQuery = true)
    double calcularDistanciaMetros(
        @Param("ubicacionId") Long ubicacionId,
        @Param("latitud") double latitud,
        @Param("longitud") double longitud
    );
}
