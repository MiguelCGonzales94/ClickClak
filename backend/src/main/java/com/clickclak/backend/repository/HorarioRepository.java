package com.clickclak.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.Horario;

public interface HorarioRepository extends JpaRepository<Horario, Long> {
}
