package com.clickclak.backend.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vínculo vigente colaborador-proyecto-ubicación-horario, versionado en el tiempo porque
 * el personal deslocalizado cambia de proyecto. Una marcación referencia la asignación
 * vigente al momento del evento, no la asignación actual del colaborador.
 */
@Entity
@Table(name = "asignacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horario_id", nullable = false)
    private Horario horario;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @Builder.Default
    private boolean activo = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;
}
