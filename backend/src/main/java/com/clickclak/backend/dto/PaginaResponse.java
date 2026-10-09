package com.clickclak.backend.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Página de resultados con solo lo que el panel necesita (sin los metadatos internos de Spring Data). */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long total, int totalPaginas) {

    public static <E, T> PaginaResponse<T> desde(Page<E> pagina, Function<E, T> convertidor) {
        return new PaginaResponse<>(
            pagina.getContent().stream().map(convertidor).toList(),
            pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
