package com.clickclak.backend.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Traduce toda excepción a una respuesta HTTP con el mismo cuerpo, {@code {"error": "..."}},
 * y sin exponer detalles internos (trazas, SQL, rutas, nombres de clases).
 *
 * <p>Hereda de {@link ResponseEntityExceptionHandler} para cubrir las excepciones estándar de
 * Spring MVC (JSON mal formado, método no permitido, ruta inexistente, parámetro inválido…): si
 * solo existiera un manejador de {@link Exception}, esas excepciones saldrían como 500.
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, String>> credencialesInvalidas(CredencialesInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<Map<String, String>> demasiadosIntentos(DemasiadosIntentosException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosRestantes()))
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> recursoNoEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DispositivoNoAutorizadoException.class)
    public ResponseEntity<Map<String, String>> dispositivoNoAutorizado(DispositivoNoAutorizadoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<Map<String, String>> solicitudInvalida(SolicitudInvalidaException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ConflictoAsignacionException.class)
    public ResponseEntity<Map<String, String>> conflictoAsignacion(ConflictoAsignacionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TransicionIncidenciaInvalidaException.class)
    public ResponseEntity<Map<String, String>> transicionIncidenciaInvalida(TransicionIncidenciaInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<Map<String, String>> recursoDuplicado(RecursoDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Los servicios lanzan esta excepción para reglas como "no puede revisar su propia
     * incidencia". Se responde con un mensaje fijo: el motivo concreto no se devuelve.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accesoDenegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", mensajeGenerico(HttpStatus.FORBIDDEN)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> noAutenticado(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", mensajeGenerico(HttpStatus.UNAUTHORIZED)));
    }

    /** Violaciones de restricciones de la base de datos: nunca se devuelve el SQL ni el nombre de la restricción. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integridadDeDatos(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "La operación viola una regla de integridad de los datos"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> parametroInvalido(ConstraintViolationException ex) {
        String detalle = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", detalle));
    }

    /** Última red: cualquier fallo no previsto sale como 500 genérico; el detalle queda solo en el log del servidor. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> errorInesperado(Exception ex, HttpServletRequest solicitud) {
        log.error("Error no controlado en {} {}", solicitud.getMethod(), solicitud.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", mensajeGenerico(HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode estado, WebRequest solicitud) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", detalle));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode estado, WebRequest solicitud) {
        // El mensaje original de Jackson nombra clases y posiciones internas: no se devuelve.
        return ResponseEntity.badRequest().body(Map.of("error", "El cuerpo de la solicitud no es válido"));
    }

    /** Todas las demás excepciones estándar de Spring MVC salen con el mismo cuerpo y un texto fijo por código. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object cuerpo, HttpHeaders headers, HttpStatusCode estado, WebRequest solicitud) {
        return ResponseEntity.status(estado).headers(headers).body(Map.of("error", mensajeGenerico(estado)));
    }

    private static String mensajeGenerico(HttpStatusCode estado) {
        return switch (estado.value()) {
            case 400 -> "Solicitud inválida";
            case 401 -> "No autenticado";
            case 403 -> "No tiene permisos para esta operación";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 406 -> "Formato de respuesta no aceptable";
            case 413 -> "La solicitud es demasiado grande";
            case 415 -> "Tipo de contenido no soportado";
            case 429 -> "Demasiadas solicitudes";
            default -> estado.is5xxServerError() ? "Error interno del servidor" : "Solicitud no válida";
        };
    }
}
