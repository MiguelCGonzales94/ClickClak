package com.clickclak.backend.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.dto.ClaveTemporalResponse;
import com.clickclak.backend.dto.EditarUsuarioRequest;
import com.clickclak.backend.dto.HistorialUsuarioResponse;
import com.clickclak.backend.dto.PaginaResponse;
import com.clickclak.backend.dto.RegistrarUsuarioRequest;
import com.clickclak.backend.dto.UsuarioResponse;
import com.clickclak.backend.exception.RecursoDuplicadoException;
import com.clickclak.backend.exception.OperacionNoPermitidaException;
import com.clickclak.backend.exception.RecursoNoEncontradoException;
import com.clickclak.backend.exception.SolicitudInvalidaException;
import com.clickclak.backend.model.AccionAuditoria;
import com.clickclak.backend.model.BitacoraAuditoria;
import com.clickclak.backend.model.EstadoCuenta;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.BitacoraAuditoriaRepository;
import com.clickclak.backend.repository.RolRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.clickclak.backend.security.AlmacenIntentosFallidos;
import com.clickclak.backend.security.GeneradorClaveTemporal;
import com.clickclak.backend.security.PoliticaContrasenas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HU04: alta, edición, activación/desactivación y eliminación de usuarios. Eliminar (borrado
 * físico) solo se permite si el usuario no tiene ningún historial: marcaciones, incidencias,
 * asignaciones, dispositivos o auditoría quedan a su nombre y borrarlo rompería la trazabilidad.
 * Con historial, la salida es desactivar. Cada operación queda en {@link BitacoraAuditoria},
 * tal como exige el criterio de aceptación.
 */
@Service
public class UsuarioService {

    static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final String MENSAJE_CON_HISTORIAL =
            "El usuario tiene historial (marcaciones, incidencias, asignaciones, dispositivos o auditoría) "
                    + "y no se puede eliminar: desactívelo en su lugar";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final BitacoraAuditoriaRepository bitacoraRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AlmacenIntentosFallidos almacenIntentosFallidos;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            BitacoraAuditoriaRepository bitacoraRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            AlmacenIntentosFallidos almacenIntentosFallidos) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.bitacoraRepository = bitacoraRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.almacenIntentosFallidos = almacenIntentosFallidos;
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
        return aRespuesta(usuario);
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
        return aRespuesta(usuario);
    }

    public UsuarioResponse cambiarEstado(Long usuarioId, boolean activo, Long actorId) {
        return cambiarEstado(usuarioId, activo, null, actorId);
    }

    /** {@code motivo} solo aplica a la baja; al reactivar se limpian la fecha y el motivo de la baja anterior. */
    @Transactional
    public UsuarioResponse cambiarEstado(Long usuarioId, boolean activo, String motivo, Long actorId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        if (!activo) {
            validarDesactivacion(usuario, actorId);
        }
        String motivoLimpio = activo || motivo == null || motivo.isBlank() ? null : motivo.trim();
        Map<String, Object> estadoAnterior = estadoDeBaja(usuario.isActivo(), usuario.getMotivoBaja());

        usuario.setActivo(activo);
        usuario.setDesactivadoEn(activo ? null : Instant.now());
        usuario.setMotivoBaja(motivoLimpio);
        usuarioRepository.save(usuario);

        registrarBitacora(actorId, usuario.getId(), AccionAuditoria.MODIFICACION, estadoAnterior,
                estadoDeBaja(activo, motivoLimpio));
        return aRespuesta(usuario);
    }

    /** HU04: levanta el bloqueo por intentos fallidos sin esperar los 15 minutos. */
    @Transactional
    public UsuarioResponse desbloquear(Long usuarioId, Long actorId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        boolean estabaBloqueado = estaBloqueado(usuario);
        almacenIntentosFallidos.limpiar(usuario.getCorreo());

        registrarBitacora(actorId, usuario.getId(), AccionAuditoria.MODIFICACION,
                Map.of("bloqueada", estabaBloqueado), Map.of("bloqueada", false));
        return aRespuesta(usuario);
    }

    /**
     * Borrado físico, solo sin historial. La comprobación explícita da el mensaje claro; la
     * captura de {@link DataIntegrityViolationException} es la red de seguridad si el esquema
     * gana una tabla nueva que {@code tieneHistorial} aún no conozca.
     */
    @Transactional
    public void eliminarUsuario(Long usuarioId, Long actorId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        if (usuario.getId().equals(actorId)) {
            throw new OperacionNoPermitidaException("No puede eliminar su propia cuenta");
        }
        if (usuario.isActivo() && esUltimoAdministradorActivo(usuario)) {
            throw new OperacionNoPermitidaException("Debe quedar al menos un administrador activo");
        }
        if (usuarioRepository.tieneHistorial(usuarioId)) {
            throw new OperacionNoPermitidaException(MENSAJE_CON_HISTORIAL);
        }

        Map<String, Object> estadoAnterior = capturarEstado(usuario);
        try {
            usuarioRepository.delete(usuario);
            usuarioRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new OperacionNoPermitidaException(MENSAJE_CON_HISTORIAL);
        }
        registrarBitacora(actorId, usuarioId, AccionAuditoria.ELIMINACION, estadoAnterior, null);
    }

    /**
     * HU04: el administrador asigna una clave temporal a otro usuario. Se devuelve una sola vez y el
     * usuario queda obligado a cambiarla en su próximo ingreso (el filtro JWT solo le deja llamar al
     * cambio de clave). Levanta también el bloqueo por intentos fallidos: quien pide una clave nueva
     * suele estar bloqueado por haber olvidado la anterior.
     */
    @Transactional
    public ClaveTemporalResponse restablecerClave(Long usuarioId, Long actorId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        if (usuario.getId().equals(actorId)) {
            throw new OperacionNoPermitidaException("Para cambiar su propia contraseña use «Cambiar mi contraseña»");
        }
        if (Rol.COLABORADOR.equals(usuario.getRol().getNombre())) {
            throw new SolicitudInvalidaException("Un colaborador no usa contraseña: su acceso es por WebAuthn");
        }
        if (!usuario.isActivo()) {
            throw new OperacionNoPermitidaException("No se puede restablecer la contraseña de un usuario inactivo");
        }

        String claveTemporal = GeneradorClaveTemporal.generar();
        boolean estabaPendiente = usuario.isDebeCambiarClave();
        usuario.setPasswordHash(passwordEncoder.encode(claveTemporal));
        usuario.setDebeCambiarClave(true);
        usuarioRepository.save(usuario);
        almacenIntentosFallidos.limpiar(usuario.getCorreo());

        // La clave nunca entra en la bitácora: solo queda constancia de que se restableció.
        registrarBitacora(actorId, usuario.getId(), AccionAuditoria.MODIFICACION,
                Map.of("debeCambiarClave", estabaPendiente),
                Map.of("debeCambiarClave", true, "clave", "restablecida por el administrador"));
        return new ClaveTemporalResponse(claveTemporal);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(Long usuarioId) {
        return aRespuesta(obtenerUsuario(usuarioId));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(String rol, Boolean activo) {
        Set<String> bloqueados = almacenIntentosFallidos.correosBloqueados();
        return usuarioRepository.buscar(rol, activo).stream()
                .map(usuario -> UsuarioResponse.desde(usuario, bloqueados.contains(usuario.getCorreo())))
                .toList();
    }

    /** HU04: búsqueda paginada del panel. {@code texto}, {@code rol} y {@code estado} son opcionales. */
    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> buscar(String texto, String rol, String estado, int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw new SolicitudInvalidaException(
                    "La página debe ser 0 o mayor y el tamaño debe estar entre 1 y " + TAMANO_MAXIMO_PAGINA);
        }
        String estadoNormalizado = estado == null || estado.isBlank() ? null : interpretarEstado(estado).name();
        String rolNormalizado = rol == null || rol.isBlank() ? null : rol;

        Set<String> bloqueados = almacenIntentosFallidos.correosBloqueados();
        // Un IN con lista vacía no es portable entre dialectos: el centinela nunca coincide con un correo.
        List<String> paraConsulta = bloqueados.isEmpty() ? List.of("") : List.copyOf(bloqueados);

        var resultado = usuarioRepository.buscarPagina(
                patronDeBusqueda(texto), rolNormalizado, estadoNormalizado, paraConsulta, PageRequest.of(pagina, tamano));
        return PaginaResponse.desde(resultado, usuario -> UsuarioResponse.desde(usuario, bloqueados.contains(usuario.getCorreo())));
    }

    /** HU04: línea de tiempo del usuario, de la más reciente a la más antigua, tomada de la bitácora. */
    @Transactional(readOnly = true)
    public List<HistorialUsuarioResponse> historial(Long usuarioId) {
        obtenerUsuario(usuarioId);
        return bitacoraRepository.findByEntidadAndEntidadIdOrderByCreadoEnDescIdDesc("usuario", usuarioId).stream()
                .map(entrada -> new HistorialUsuarioResponse(
                        entrada.getId(),
                        entrada.getAccion(),
                        entrada.getUsuario() != null ? entrada.getUsuario().getId() : null,
                        entrada.getUsuario() != null
                                ? entrada.getUsuario().getNombres() + " " + entrada.getUsuario().getApellidos()
                                : null,
                        aJsonNode(entrada.getValoresAnteriores()),
                        aJsonNode(entrada.getValoresNuevos()),
                        entrada.getCreadoEn()))
                .toList();
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

    private UsuarioResponse aRespuesta(Usuario usuario) {
        return UsuarioResponse.desde(usuario, estaBloqueado(usuario));
    }

    private boolean estaBloqueado(Usuario usuario) {
        return almacenIntentosFallidos.tiempoRestanteDeBloqueo(usuario.getCorreo()).isPresent();
    }

    private EstadoCuenta interpretarEstado(String estado) {
        try {
            return EstadoCuenta.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new SolicitudInvalidaException("Estado de cuenta desconocido: " + estado);
        }
    }

    /** Texto libre a patrón LIKE en minúsculas; escapa los comodines para que "50%" se busque literal. */
    private String patronDeBusqueda(String texto) {
        if (texto == null || texto.isBlank()) {
            return "%";
        }
        String escapado = texto.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }

    private Map<String, Object> estadoDeBaja(boolean activo, String motivo) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("activo", activo);
        if (motivo != null) {
            estado.put("motivoBaja", motivo);
        }
        return estado;
    }

    private JsonNode aJsonNode(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo leer el registro de auditoría", ex);
        }
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
