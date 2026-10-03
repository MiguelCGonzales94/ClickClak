package com.clickclak.backend.exception;

/** La cuenta está bloqueada temporalmente por exceso de intentos fallidos de acceso. */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosRestantes;

    public DemasiadosIntentosException(long segundosRestantes) {
        super("Demasiados intentos fallidos. Intente de nuevo más tarde.");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
