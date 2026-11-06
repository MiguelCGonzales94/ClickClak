package com.clickclak.backend.model;

/** Cómo llegó una persona respecto del inicio de su turno; solo aplica a las entradas con turno asignado. */
public enum EstadoPuntualidad {
    /** Antes de la hora de inicio del turno. */
    TEMPRANO,
    /** Desde la hora de inicio hasta el fin de la tolerancia del turno (inclusive). */
    DENTRO_DE_TOLERANCIA,
    /** Pasada la tolerancia: es la misma regla con la que se genera la incidencia de tardanza. */
    TARDE
}
