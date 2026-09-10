package com.clickclak.backend.exception;

/** Datos sintácticamente válidos que violan una regla de negocio (no cubierta por bean validation). */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
