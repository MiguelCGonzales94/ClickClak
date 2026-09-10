package com.clickclak.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.locationtech.jts.geom.Point;

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

/**
 * Evento de jornada capturado en el dispositivo. {@code uuidCliente} lo genera la PWA en el
 * momento del evento (offline) y permite deduplicar reintentos de sincronización; por eso
 * {@code horaEvento} (cuándo ocurrió) se guarda separado de {@code horaSincronizacion}
 * (cuándo llegó al servidor) — ver OE4.
 */
@Entity
@Table(name = "marcacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marcacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID uuidCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Asignación vigente al momento del evento — no necesariamente la asignación actual. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignacion_id")
    private Asignacion asignacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    private Dispositivo dispositivo;

    @Enumerated(EnumType.STRING)
    private TipoEvento tipoEvento;

    private Instant horaEvento;

    @CreationTimestamp
    private Instant horaSincronizacion;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point geom;

    private BigDecimal precisionMetros;

    /** Distancia calculada contra la ubicación de la asignación, persistida para auditoría. */
    private BigDecimal distanciaMetros;

    @Enumerated(EnumType.STRING)
    private EstadoValidacion estadoValidacion;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;
}
