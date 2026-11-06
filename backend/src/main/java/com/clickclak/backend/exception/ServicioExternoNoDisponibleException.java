package com.clickclak.backend.exception;

/** Un servicio externo del que depende una función accesoria (p. ej. la dirección de un punto) no respondió. */
public class ServicioExternoNoDisponibleException extends RuntimeException {
    public ServicioExternoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
