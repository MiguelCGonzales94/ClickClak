package com.clickclak.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.Dispositivo;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {
    Optional<Dispositivo> findByCredentialId(String credentialId);

    List<Dispositivo> findByUsuarioIdAndActivoTrue(Long usuarioId);
}
