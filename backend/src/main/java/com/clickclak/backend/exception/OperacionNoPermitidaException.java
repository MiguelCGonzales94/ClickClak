package com.clickclak.backend.exception;

/** La solicitud es válida, pero el estado actual del sistema impide la operación (por ejemplo, dejar sin administradores). */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
