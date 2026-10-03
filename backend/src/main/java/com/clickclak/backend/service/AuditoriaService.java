package com.clickclak.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.BitacoraAuditoria;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Deja constancia en la bitácora de quién modificó qué entidad y con qué valores (OE6). Se
 * ejecuta siempre dentro de la transacción del llamador: si el cambio se deshace, también su
 * rastro, y si la auditoría falla, el cambio no se confirma sin evidencia.
 */
@Service
public class AuditoriaService {

    private final BitacoraAuditoriaRepository bitacoraRepository;
    private final ObjectMapper objectMapper;

    public AuditoriaService(BitacoraAuditoriaRepository bitacoraRepository, ObjectMapper objectMapper) {
        this.bitacoraRepository = bitacoraRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param actor      quién realizó la acción; {@code null} cuando la hizo el sistema (p. ej. el motor de validación)
     * @param anteriores estado previo, o {@code null} en una creación
     * @param nuevos     estado resultante
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Usuario actor, String entidad, Long entidadId, AccionAuditoria accion,
                          Object anteriores, Object nuevos) {
        bitacoraRepository.save(BitacoraAuditoria.builder()
                .usuario(actor)
                .entidad(entidad)
                .entidadId(entidadId)
                .accion(accion)
                .valoresAnteriores(aJson(anteriores))
                .valoresNuevos(aJson(nuevos))
                .build());
    }

    private String aJson(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el valor a auditar", ex);
        }
    }
}
