package com.clickclak.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.IncidenciaAdjunto;

public interface IncidenciaAdjuntoRepository extends JpaRepository<IncidenciaAdjunto, Long> {
    List<IncidenciaAdjunto> findByIncidenciaId(Long incidenciaId);
}
