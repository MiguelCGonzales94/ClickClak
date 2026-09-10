package com.clickclak.backend.model;

/** Estado graduado que produce el motor de validación contextual (OE3) — nunca binario. */
public enum EstadoValidacion {
    VALIDO,
    OBSERVADO,
    FUERA_DE_TOLERANCIA,
    SOSPECHOSO,
    /** El colaborador no tiene una asignación vigente contra la cual validar. */
    SIN_ASIGNACION
}
