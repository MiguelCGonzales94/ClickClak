package com.clickclak.backend.model;

/**
 * HU04: estado visible de la cuenta de un usuario. No se guarda: se calcula al responder con
 * esta precedencia — INACTIVA, BLOQUEADA, CLAVE_PENDIENTE, ACTIVA — porque una cuenta dada de
 * baja manda sobre cualquier otro estado, y un bloqueo por intentos fallidos impide entrar aunque
 * la clave sea temporal.
 */
public enum EstadoCuenta {
    ACTIVA,
    INACTIVA,
    /** 5 contraseñas incorrectas seguidas; el bloqueo vive en memoria (ver AlmacenIntentosFallidos). */
    BLOQUEADA,
    /** El administrador restableció la clave y el usuario todavía no la cambió. */
    CLAVE_PENDIENTE;

    public static EstadoCuenta de(Usuario usuario, boolean bloqueada) {
        if (!usuario.isActivo()) {
            return INACTIVA;
        }
        if (bloqueada) {
            return BLOQUEADA;
        }
        return usuario.isDebeCambiarClave() ? CLAVE_PENDIENTE : ACTIVA;
    }
}
