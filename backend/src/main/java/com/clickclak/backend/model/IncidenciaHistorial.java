package com.clickclak.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Traza cada cambio de estado del flujo de una incidencia, para trazabilidad completa (OE5). */
@Entity
@Table(name = "incidencia_historial")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidenciaHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incidencia_id", nullable = false)
    private Incidencia incidencia;

    @Enumerated(EnumType.STRING)
    private EstadoIncidencia estadoAnterior;

    @Enumerated(EnumType.STRING)
    private EstadoIncidencia estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(columnDefinition = "text")
    private String comentario;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;
}
