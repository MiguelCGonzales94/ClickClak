package com.clickclak.backend.model;

import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "horario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Horario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private LocalTime horaInicio;

    private LocalTime horaFin;

    private LocalTime horaInicioRefrigerio;

    private LocalTime horaFinRefrigerio;

    @Builder.Default
    private int toleranciaMinutos = 10;

    @Builder.Default
    private String diasSemana = "L,M,X,J,V";
}
