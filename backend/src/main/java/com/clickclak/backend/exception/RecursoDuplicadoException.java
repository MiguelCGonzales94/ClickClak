package com.clickclak.backend.exception;

/** Violación de una restricción de unicidad detectada antes de intentar el insert/update. */
public class RecursoDuplicadoException extends RuntimeException {
    public RecursoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
