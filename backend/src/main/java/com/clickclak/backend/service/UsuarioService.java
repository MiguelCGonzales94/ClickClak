package com.clickclak.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.EditarUsuarioRequest;
import com.clickclak.backend.dto.RegistrarUsuarioRequest;
import com.clickclak.backend.dto.UsuarioResponse;
import com.clickclak.backend.exception.RecursoDuplicadoException;
import com.clickclak.backend.exception.OperacionNoPermitidaException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.BitacoraAuditoria;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.PoliticaContrasenas;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HU04: alta, edición y activación/desactivación de usuarios. Nunca se elimina un usuario
 * (rompería la trazabilidad de marcaciones/incidencias ya registradas a su nombre) — por
 * eso la HU pide "activar y desactivar", no "eliminar". Cada operación queda en
 * {@link BitacoraAuditoria}, tal como exige el criterio de aceptación.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final BitacoraAuditoriaRepository bitacoraRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            BitacoraAuditoriaRepository bitacoraRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UsuarioResponse registrarUsuario(RegistrarUsuarioRequest solicitud, Long actorId) {
        String correo = normalizarCorreo(solicitud.correo());
        if (usuarioRepository.findByCorreo(correo).isPresent()) {
            throw new RecursoDuplicadoException("Ya existe un usuario con el correo " + correo);
        }
        if (usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(solicitud.tipoDocumento(), solicitud.numeroDocumento())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un usuario con documento " + solicitud.tipoDocumento() + " " + solicitud.numeroDocumento());
        }

        Rol rol = buscarRol(solicitud.rol());
        String passwordHash = calcularPasswordHash(rol, solicitud.password());

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombres(solicitud.nombres())
                .apellidos(solicitud.apellidos())
                .tipoDocumento(solicitud.tipoDocumento())
                .numeroDocumento(solicitud.numeroDocumento())
                .correo(correo)
                .rol(rol)
                .passwordHash(passwordHash)
                .activo(true)
                .build());

        registrarBitacora(actorId, usuario.getId(), AccionAuditoria.CREACION, null, capturarEstado(usuario));
        return UsuarioResponse.desde(usuario);
    }

    @Transactional
    public UsuarioResponse editarUsuario(Long usuarioId, EditarUsuarioRequest solicitud, Long actorId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        Map<String, Object> estadoAnterior = capturarEstado(usuario);

        String correo = normalizarCorreo(solicitud.correo());
        boolean cambiaCorreo = !usuario.getCorreo().equalsIgnoreCase(correo);
        if (cambiaCorreo && usuarioRepository.findByCorreo(correo).isPresent()) {
            throw new RecursoDuplicadoException("Ya existe un usuario con el correo " + correo);
        }
        boolean cambiaDocumento = !usuario.getTipoDocumento().equals(solicitud.tipoDocumento())
                || !usuario.getNumeroDocumento().equals(solicitud.numeroDocumento());
        if (cambiaDocumento && usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(
                solicitud.tipoDocumento(), solicitud.numeroDocumento())) {
            throw new RecursoDuplicadoException(
                    "Ya existe un usuario con documento " + solicitud.tipoDocumento() + " " + solicitud.numeroDocumento());
        }

        Rol rol = buscarRol(solicitud.rol());
        boolean cambiaRol = !usuario.getRol().getNombre().equals(rol.getNombre());
        if (cambiaRol) {
            validarCambioDeRol(usuario, actorId);
        }

        usuario.setNombres(solicitud.nombres());
        usuario.setApellidos(solicitud.apellidos());
        usuario.setTipoDocumento(solicitud.tipoDocumento());
        usuario.setNumeroDocumento(solicitud.numeroDocumento());
        usuario.setCorreo(correo);
        aplicarRolYContrasena(usuario, rol, solicitud.password());
        usuarioRepository.save(usuario);

        registrarBitacora(actorId, usuario.getId(), AccionAuditoria.MODIFICACION, estadoAnterior, capturarEstado(usuario));
        return UsuarioResponse.desde(usuario);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long usuarioId, boolean activo, Long actorId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        if (!activo) {
            validarDesactivacion(usuario, actorId);
        }
        Map<String, Object> estadoAnterior = Map.of("activo", usuario.isActivo());

        usuario.setActivo(activo);
        usuarioRepository.save(usuario);

        registrarBitacora(actorId, usuario.getId(), AccionAuditoria.MODIFICACION, estadoAnterior, Map.of("activo", activo));
        return UsuarioResponse.desde(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Long usuarioId) {
        return UsuarioResponse.desde(obtenerUsuario(usuarioId));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(String rol, Boolean activo) {
        return usuarioRepository.buscar(rol, activo).stream().map(UsuarioResponse::desde).toList();
    }

    private Usuario obtenerUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + usuarioId));
    }

    private Rol buscarRol(String nombreRol) {
        return rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol no encontrado: " + nombreRol));
    }

    /**
     * Un COLABORADOR nunca tiene contraseña (accede por WebAuthn, ver HU05); SUPERVISOR y
     * RRHH_ADMIN la necesitan porque hoy solo tienen login clásico.
     */
    private String calcularPasswordHash(Rol rol, String password) {
        boolean esColaborador = Rol.COLABORADOR.equals(rol.getNombre());
        boolean tienePassword = password != null && !password.isBlank();

        if (esColaborador && tienePassword) {
            throw new SolicitudInvalidaException("Un colaborador no debe tener contraseña: su acceso es por WebAuthn");
        }
        if (!esColaborador && !tienePassword) {
            throw new SolicitudInvalidaException("El rol " + rol.getNombre() + " requiere contraseña para iniciar sesión");
        }
        if (tienePassword) {
            PoliticaContrasenas.validar(password);
        }
        return tienePassword ? passwordEncoder.encode(password) : null;
    }

    /**
     * Un COLABORADOR no tiene contraseña y los demás roles sí, así que cambiar entre ellos obliga a
     * crearla o a borrarla. Sin cambio de tipo de rol no se acepta {@code password}: cambiar la clave
     * de un usuario existente tiene sus propios endpoints (propia o restablecimiento por el admin).
     */
    private void aplicarRolYContrasena(Usuario usuario, Rol rolNuevo, String password) {
        boolean eraColaborador = Rol.COLABORADOR.equals(usuario.getRol().getNombre());
        boolean seraColaborador = Rol.COLABORADOR.equals(rolNuevo.getNombre());
        boolean tienePassword = password != null && !password.isBlank();

        if (eraColaborador != seraColaborador) {
            usuario.setPasswordHash(calcularPasswordHash(rolNuevo, password));
        } else if (tienePassword) {
            throw new SolicitudInvalidaException(
                    "Para cambiar la contraseña de un usuario use el restablecimiento de contraseña");
        }
        usuario.setRol(rolNuevo);
    }

    private void validarCambioDeRol(Usuario usuario, Long actorId) {
        if (usuario.getId().equals(actorId)) {
            throw new OperacionNoPermitidaException("No puede cambiar su propio rol");
        }
        if (usuario.isActivo() && esUltimoAdministradorActivo(usuario)) {
            throw new OperacionNoPermitidaException("Debe quedar al menos un administrador activo");
        }
    }

    private void validarDesactivacion(Usuario usuario, Long actorId) {
        if (usuario.getId().equals(actorId)) {
            throw new OperacionNoPermitidaException("No puede desactivar su propia cuenta");
        }
        if (usuario.isActivo() && esUltimoAdministradorActivo(usuario)) {
            throw new OperacionNoPermitidaException("Debe quedar al menos un administrador activo");
        }
    }

    private boolean esUltimoAdministradorActivo(Usuario usuario) {
        return Rol.RRHH_ADMIN.equals(usuario.getRol().getNombre())
                && usuarioRepository.countByRolNombreAndActivoTrue(Rol.RRHH_ADMIN) <= 1;
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> capturarEstado(Usuario usuario) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nombres", usuario.getNombres());
        estado.put("apellidos", usuario.getApellidos());
        estado.put("tipoDocumento", usuario.getTipoDocumento());
        estado.put("numeroDocumento", usuario.getNumeroDocumento());
        estado.put("correo", usuario.getCorreo());
        estado.put("rol", usuario.getRol().getNombre());
        estado.put("activo", usuario.isActivo());
        return estado;
    }

    private void registrarBitacora(
            Long actorId, Long usuarioAfectadoId, AccionAuditoria accion,
            Map<String, Object> valoresAnteriores, Map<String, Object> valoresNuevos) {
        bitacoraRepository.save(BitacoraAuditoria.builder()
                .usuario(actorId != null ? usuarioRepository.getReferenceById(actorId) : null)
                .entidad("usuario")
                .entidadId(usuarioAfectadoId)
                .accion(accion)
                .valoresAnteriores(aJson(valoresAnteriores))
                .valoresNuevos(aJson(valoresNuevos))
                .build());
    }

    private String aJson(Map<String, Object> valores) {
        if (valores == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(valores);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el registro de auditoría", ex);
        }
    }
}
