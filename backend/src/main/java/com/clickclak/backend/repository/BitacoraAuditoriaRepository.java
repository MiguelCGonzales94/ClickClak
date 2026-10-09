package com.clickclak.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.BitacoraAuditoria;

public interface BitacoraAuditoriaRepository extends JpaRepository<BitacoraAuditoria, Long> {
    List<BitacoraAuditoria> findByEntidadAndEntidadId(String entidad, Long entidadId);

    List<BitacoraAuditoria> findByEntidadAndEntidadIdOrderByCreadoEnDescIdDesc(String entidad, Long entidadId);
}
