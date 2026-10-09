package com.clickclak.backend.security;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Genera las claves temporales que el administrador entrega al restablecer una contraseña
 * (HU04). Cumple {@link PoliticaContrasenas} (letras y números) por construcción y evita los
 * caracteres que se confunden al dictarlas: sin 0/O, 1/l/I.
 */
public final class GeneradorClaveTemporal {

    static final int LETRAS = 8;
    static final int DIGITOS = 4;
    private static final String ALFABETO_LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String ALFABETO_DIGITOS = "23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeneradorClaveTemporal() {
    }

    public static String generar() {
        List<Character> caracteres = new ArrayList<>(LETRAS + DIGITOS);
        for (int i = 0; i < LETRAS; i++) {
            caracteres.add(ALFABETO_LETRAS.charAt(ALEATORIO.nextInt(ALFABETO_LETRAS.length())));
        }
        for (int i = 0; i < DIGITOS; i++) {
            caracteres.add(ALFABETO_DIGITOS.charAt(ALEATORIO.nextInt(ALFABETO_DIGITOS.length())));
        }
        Collections.shuffle(caracteres, ALEATORIO);

        StringBuilder clave = new StringBuilder(caracteres.size());
        caracteres.forEach(clave::append);
        return clave.toString();
    }
}
