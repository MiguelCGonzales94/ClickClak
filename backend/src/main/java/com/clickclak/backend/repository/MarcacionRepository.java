package com.clickclak.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.clickclak.backend.model.Marcacion;

/** La consulta de asistencia usa {@link EspecificacionesMarcacion}, de ahí el {@code JpaSpecificationExecutor}. */
public interface MarcacionRepository extends JpaRepository<Marcacion, Long>, JpaSpecificationExecutor<Marcacion> {
    Optional<Marcacion> findByUuidCliente(UUID uuidCliente);

    List<Marcacion> findByUsuarioIdAndHoraEventoBetween(Long usuarioId, Instant desde, Instant hasta);
}
