package com.clickclak.backend.model;

import java.time.Instant;

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

/** Sustento documental de una incidencia (OE5). Solo la ruta vive en BD; el archivo va al volumen del backend. */
@Entity
@Table(name = "incidencia_adjunto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidenciaAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incidencia_id", nullable = false)
    private Incidencia incidencia;

    private String nombreArchivo;

    private String rutaAlmacenamiento;

    private String tipoMime;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant subidoEn;
}
