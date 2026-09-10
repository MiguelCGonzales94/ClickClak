package com.clickclak.backend.service;

/**
 * HU03: "se verifica la identidad por un canal autorizado" — el canal es el correo
 * corporativo del usuario. Interfaz separada de {@link RecuperacionClaveService} para que
 * el envío real (SMTP) pueda añadirse después sin tocar la lógica de negocio; hoy no hay
 * infraestructura de correo configurada en el proyecto, así que la única implementación
 * existente ({@link NotificadorRecuperacionLog}) es un sustituto de desarrollo.
 */
public interface NotificadorRecuperacion {
    void enviarEnlaceRecuperacion(String correo, String token);
}
