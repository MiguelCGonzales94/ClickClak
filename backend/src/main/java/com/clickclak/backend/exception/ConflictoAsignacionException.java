package com.clickclak.backend.exception;

public class ConflictoAsignacionException extends RuntimeException {
    public ConflictoAsignacionException(Long usuarioId) {
        super("El usuario " + usuarioId + " ya tiene una asignación activa en ese rango de fechas");
    }
}
