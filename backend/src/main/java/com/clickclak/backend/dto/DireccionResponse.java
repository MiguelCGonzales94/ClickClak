package com.clickclak.backend.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dirección aproximada de un punto, tal como la devuelve la geocodificación inversa de OpenStreetMap
 * (Nominatim). Los nombres de los campos son los del servicio, y se conservan para que el panel los
 * formatee: {@code address} trae la calle, la zona y el distrito. Es solo una ayuda para leer el punto;
 * no se guarda.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DireccionResponse(
    @JsonProperty("display_name") String displayName,
    Map<String, String> address,
    String error
) {
}
