package com.clickclak.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Respuestas JSON consistentes para 401/403 — nunca la página HTML por defecto de Spring Security. */
@Component
public class ManejadorErroresAutenticacion implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ManejadorErroresAutenticacion(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escribirError(response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escribirError(response, HttpServletResponse.SC_FORBIDDEN, "No tiene permisos para esta operación");
    }

    /** HU04: el usuario entró con una clave temporal y debe cambiarla antes de usar el resto de la API. */
    public void clavePendiente(HttpServletResponse response) throws IOException {
        escribirJson(response, HttpServletResponse.SC_FORBIDDEN,
                Map.of("error", "Debe cambiar su contraseña temporal antes de continuar", "codigo", "CLAVE_PENDIENTE"));
    }

    private void escribirError(HttpServletResponse response, int status, String mensaje) throws IOException {
        escribirJson(response, status, Map.of("error", mensaje));
    }

    /**
     * Sin fijar la codificación, el contenedor escribe con ISO-8859-1 y la "ñ" o la "ó" de los
     * mensajes llegan como un byte que no es UTF-8 válido: el cliente ve texto roto y cualquier
     * lector estricto de JSON falla. Se detectó al verificar el despliegue con `sed`.
     */
    private void escribirJson(HttpServletResponse response, int status, Map<String, String> cuerpo) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(cuerpo));
    }
}
