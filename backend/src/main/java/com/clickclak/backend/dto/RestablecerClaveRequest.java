package com.clickclak.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerClaveRequest(
    @NotBlank @Size(max = 100) String token,
    @NotBlank @Size(max = 72) String nuevaPassword
) {
}
