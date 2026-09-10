package com.clickclak.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.EstadoIncidencia;
import com.clickclak.backend.model.Incidencia;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {
    List<Incidencia> findByUsuarioId(Long usuarioId);

    List<Incidencia> findByEstado(EstadoIncidencia estado);
}
