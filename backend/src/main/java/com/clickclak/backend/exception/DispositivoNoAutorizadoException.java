package com.clickclak.backend.exception;

public class DispositivoNoAutorizadoException extends RuntimeException {
    public DispositivoNoAutorizadoException(Long dispositivoId) {
        super("El dispositivo " + dispositivoId + " no está autorizado o fue revocado");
    }
}
