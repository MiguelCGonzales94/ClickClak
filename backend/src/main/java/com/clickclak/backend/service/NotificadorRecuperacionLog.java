package com.clickclak.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sustituto de desarrollo mientras no exista un servicio de correo real (SMTP) configurado
 * en el proyecto: deja el enlace en el log del backend en vez de enviarlo. El resto del
 * flujo de HU03 (token de un solo uso, vigencia de 30 min, política de contraseña) es el
 * mismo que tendría con envío real — solo cambia este último paso.
 */
@Service
public class NotificadorRecuperacionLog implements NotificadorRecuperacion {

    private static final Logger log = LoggerFactory.getLogger(NotificadorRecuperacionLog.class);

    @Override
    public void enviarEnlaceRecuperacion(String correo, String token) {
        log.info("Enlace de recuperación de contraseña para {}: /recuperar-clave?token={} (vigente 30 minutos)",
                correo, token);
    }
}
