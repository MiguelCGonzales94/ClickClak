package com.clickclak.backend.dto;

import jakarta.validation.constraints.Size;

/** HU04: motivo opcional de la baja, que queda en el usuario y en la bitácora. */
public record DesactivarUsuarioRequest(@Size(max = 255) String motivo) {
}
