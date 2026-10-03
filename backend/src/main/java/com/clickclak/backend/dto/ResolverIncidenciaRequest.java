package com.clickclak.backend.dto;

import jakarta.validation.constraints.Size;

/** Comentario que acompaña un cambio de estado; obligatorio solo al rechazar. */
public record ResolverIncidenciaRequest(
    @Size(max = 1000) String comentario
) {
}
