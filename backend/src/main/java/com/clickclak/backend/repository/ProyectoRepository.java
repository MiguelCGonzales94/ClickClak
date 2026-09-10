package com.clickclak.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clickclak.backend.model.Proyecto;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
}
