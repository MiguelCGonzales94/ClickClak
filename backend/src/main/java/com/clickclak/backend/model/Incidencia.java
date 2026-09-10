package com.clickclak.backend.model;

import java.time.Instant;
import java.time.LocalDate;

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
import org.hibernate.annotations.UpdateTimestamp;

/** Flujo: {@link EstadoIncidencia#REGISTRADA} -> EN_REVISION -> APROBADA/RECHAZADA -> CERRADA. */
@Entity
@Table(name = "incidencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Marcación concreta afectada, cuando aplica (ej. una tardanza sobre una marcación real). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marcacion_id")
    private Marcacion marcacion;

    @Enumerated(EnumType.STRING)
    private TipoIncidencia tipo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EstadoIncidencia estado = EstadoIncidencia.REGISTRADA;

    private LocalDate fechaEvento;

    @Column(columnDefinition = "text")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por_id")
    private Usuario revisadoPor;

    @Column(columnDefinition = "text")
    private String comentarioRevision;

    private Instant revisadoEn;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    private Instant actualizadoEn;
}
