package com.clickclak.backend.exception;

/** Mensaje deliberadamente genérico: no revela si el correo existe o si falló la contraseña. */
public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException() {
        super("Correo o contraseña inválidos");
    }
}
