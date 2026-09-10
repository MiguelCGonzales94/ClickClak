package com.clickclak.backend.model;

import org.locationtech.jts.geom.Point;

import jakarta.persistence.Column;
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
 * Sede o frente de trabajo de un proyecto. El radio de tolerancia es el margen que usa
 * el motor de validación contextual (OE3) para no emitir juicios binarios ante la
 * imprecisión propia de la geolocalización en interiores.
 */
@Entity
@Table(name = "ubicacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ubicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    private String nombre;

    private String direccionReferencia;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point geom;

    @Builder.Default
    private int radioToleranciaMetros = 150;

    @Builder.Default
    private boolean activo = true;
}
