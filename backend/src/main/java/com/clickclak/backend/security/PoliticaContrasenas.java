package com.clickclak.backend.security;

import com.clickclak.backend.exception.SolicitudInvalidaException;

/**
 * Política de contraseñas para SUPERVISOR/RRHH_ADMIN (los únicos roles con password — ver
 * {@link com.clickclak.backend.service.UsuarioService}). Se aplica tanto al crear un usuario
 * como al restablecer su clave (HU03), para que ambos caminos exijan lo mismo.
 */
public final class PoliticaContrasenas {

    private static final int LONGITUD_MINIMA = 8;

    private PoliticaContrasenas() {
    }

    public static void validar(String password) {
        if (password == null || password.length() < LONGITUD_MINIMA) {
            throw new SolicitudInvalidaException(
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw new SolicitudInvalidaException("La contraseña debe combinar letras y números");
        }
    }
}
