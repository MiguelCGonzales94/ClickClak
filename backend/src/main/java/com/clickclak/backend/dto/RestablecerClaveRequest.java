package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RestablecerClaveRequest(
    @NotBlank String token,
    @NotBlank String nuevaPassword
) {
}
