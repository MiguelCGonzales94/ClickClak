package com.clickclak.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.Marcacion;

public interface MarcacionRepository extends JpaRepository<Marcacion, Long> {
    Optional<Marcacion> findByUuidCliente(UUID uuidCliente);

    List<Marcacion> findByUsuarioIdAndHoraEventoBetween(Long usuarioId, Instant desde, Instant hasta);
}
