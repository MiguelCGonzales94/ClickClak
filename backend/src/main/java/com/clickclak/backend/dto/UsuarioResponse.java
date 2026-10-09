package com.clickclak.backend.dto;

import java.time.Instant;

import com.clickclak.backend.model.EstadoCuenta;
import com.clickclak.backend.model.Usuario;

public record UsuarioResponse(
    Long id,
    String nombres,
    String apellidos,
    String tipoDocumento,
    String numeroDocumento,
    String correo,
    String rol,
    boolean activo,
    EstadoCuenta estadoCuenta,
    Instant desactivadoEn,
    String motivoBaja,
    Instant creadoEn
) {
    /** {@code bloqueada} lo informa quien llama: el bloqueo por intentos fallidos no está en la entidad. */
    public static UsuarioResponse desde(Usuario usuario, boolean bloqueada) {
        return new UsuarioResponse(
            usuario.getId(), usuario.getNombres(), usuario.getApellidos(),
            usuario.getTipoDocumento(), usuario.getNumeroDocumento(), usuario.getCorreo(),
            usuario.getRol().getNombre(), usuario.isActivo(), EstadoCuenta.de(usuario, bloqueada),
            usuario.getDesactivadoEn(), usuario.getMotivoBaja(), usuario.getCreadoEn());
    }
}
