package com.clickclak.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.IncidenciaHistorial;

public interface IncidenciaHistorialRepository extends JpaRepository<IncidenciaHistorial, Long> {
    List<IncidenciaHistorial> findByIncidenciaIdOrderByCreadoEnAsc(Long incidenciaId);
}
