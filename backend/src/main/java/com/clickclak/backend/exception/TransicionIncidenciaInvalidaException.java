package com.clickclak.backend.exception;

import com.clickclak.backend.model.EstadoIncidencia;

/** La incidencia no puede pasar del estado actual al solicitado según el flujo de revisión. */
public class TransicionIncidenciaInvalidaException extends RuntimeException {
    public TransicionIncidenciaInvalidaException(EstadoIncidencia actual, EstadoIncidencia solicitado) {
        super("La incidencia está " + actual + " y no puede pasar a " + solicitado);
    }
}
