/** Estos tipos reflejan uno a uno los DTOs del backend (com.clickclak.backend.dto). */

export interface SolicitudLogin {
  correo: string;
  password: string;
}

export interface RespuestaLogin {
  token: string;
  expiraEnMinutos: number;
  rol: string;
  nombres: string;
  apellidos: string;
  /** Verdadero tras un restablecimiento del administrador: debe cambiar la clave antes de seguir. */
  debeCambiarClave: boolean;
}

export interface RespuestaPerfil {
  id: number;
  nombres: string;
  apellidos: string;
  correo: string;
  rol: string;
  debeCambiarClave: boolean;
}

export interface ErrorApi {
  error: string;
}

export type EstadoCuenta = "ACTIVA" | "INACTIVA" | "BLOQUEADA" | "CLAVE_PENDIENTE";

export interface RespuestaUsuario {
  id: number;
  nombres: string;
  apellidos: string;
  tipoDocumento: string;
  numeroDocumento: string;
  correo: string;
  rol: string;
  activo: boolean;
  estadoCuenta: EstadoCuenta;
  desactivadoEn: string | null;
  motivoBaja: string | null;
  creadoEn: string;
}

export interface RespuestaPagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  total: number;
  totalPaginas: number;
}

export interface FiltrosUsuarios {
  texto: string;
  rol: string;
  estado: EstadoCuenta | "";
  pagina: number;
  tamano: number;
}

export interface RespuestaHistorialUsuario {
  id: number;
  accion: "CREACION" | "MODIFICACION" | "ELIMINACION";
  actorId: number | null;
  actorNombre: string | null;
  valoresAnteriores: Record<string, unknown> | null;
  valoresNuevos: Record<string, unknown> | null;
  creadoEn: string;
}

export interface SolicitudCambiarClave {
  claveActual: string;
  claveNueva: string;
}

export interface RespuestaClaveTemporal {
  claveTemporal: string;
}

export interface RespuestaMensaje {
  mensaje: string;
}

export interface SolicitudRegistrarUsuario {
  nombres: string;
  apellidos: string;
  tipoDocumento: string;
  numeroDocumento: string;
  correo: string;
  rol: string;
  password?: string;
}

export interface SolicitudEditarUsuario {
  nombres: string;
  apellidos: string;
  tipoDocumento: string;
  numeroDocumento: string;
  correo: string;
  rol: string;
  /** Solo cuando el cambio de rol lo exige (de COLABORADOR a SUPERVISOR o RRHH_ADMIN). */
  password?: string;
}

export interface RespuestaProyecto {
  id: number;
  nombre: string;
  cliente: string;
  fechaInicio: string;
  fechaFin: string | null;
  activo: boolean;
}

export interface SolicitudRegistrarProyecto {
  nombre: string;
  cliente: string;
  fechaInicio: string;
  fechaFin?: string | null;
}

export type EstadoValidacion = "VALIDO" | "OBSERVADO" | "FUERA_DE_TOLERANCIA" | "SOSPECHOSO" | "SIN_ASIGNACION";

export type TipoEvento = "ENTRADA" | "INICIO_REFRIGERIO" | "FIN_REFRIGERIO" | "SALIDA";

/** Una marcación vista por supervisión (`GET /api/marcaciones`). Proyecto y sede son nulos si quedó SIN_ASIGNACION. */
export interface RespuestaAsistencia {
  id: number;
  usuarioId: number;
  nombreUsuario: string;
  tipoEvento: TipoEvento;
  horaEvento: string;
  horaSincronizacion: string;
  retrasoSincronizacionSegundos: number;
  estadoValidacion: EstadoValidacion;
  distanciaMetros: number | null;
  precisionMetros: number | null;
  latitud: number | null;
  longitud: number | null;
  proyecto: string | null;
  ubicacion: string | null;
  radioToleranciaMetros: number | null;
  dispositivo: string | null;
}

export interface RespuestaResumenAsistencia {
  total: number;
  porEstado: Record<EstadoValidacion, number>;
}

export interface FiltrosAsistencia {
  usuarioId: string;
  ubicacionId: string;
  desde: string;
  hasta: string;
  estado: EstadoValidacion | "";
  tipoEvento: TipoEvento | "";
  pagina: number;
  tamano: number;
}

export interface RespuestaUbicacion {
  id: number;
  proyectoId: number;
  nombre: string;
  direccionReferencia: string | null;
  latitud: number;
  longitud: number;
  radioToleranciaMetros: number;
  activo: boolean;
}

export interface SolicitudRegistrarUbicacion {
  proyectoId: number;
  nombre: string;
  direccionReferencia?: string;
  latitud: number;
  longitud: number;
  radioToleranciaMetros?: number;
}

export interface RespuestaHorario {
  id: number;
  nombre: string;
  horaInicio: string;
  horaFin: string;
  horaInicioRefrigerio: string | null;
  horaFinRefrigerio: string | null;
  toleranciaMinutos: number;
  diasSemana: string;
}

export interface SolicitudRegistrarHorario {
  nombre: string;
  horaInicio: string;
  horaFin: string;
  horaInicioRefrigerio?: string;
  horaFinRefrigerio?: string;
  toleranciaMinutos?: number;
  diasSemana?: string;
}

export interface RespuestaAsignacion {
  id: number;
  usuarioId: number;
  nombreUsuario: string;
  proyectoId: number;
  nombreProyecto: string;
  ubicacionId: number;
  nombreUbicacion: string;
  horarioId: number;
  nombreHorario: string;
  fechaInicio: string;
  fechaFin: string | null;
  estado: EstadoAsignacion;
}

/** PROGRAMADA aún no empezó, VIGENTE está en curso y FINALIZADA ya terminó (el servidor lo calcula contra hoy). */
export type EstadoAsignacion = "VIGENTE" | "PROGRAMADA" | "FINALIZADA";

export interface SolicitudEditarAsignacion {
  proyectoId: number;
  ubicacionId: number;
  horarioId: number;
  fechaInicio: string;
  fechaFin?: string | null;
}

/** Pasa al técnico a otra sede desde `fechaCambio`; proyecto y turno son opcionales y, si faltan, se conservan. */
export interface SolicitudMoverAsignacion {
  ubicacionId: number;
  proyectoId?: number;
  horarioId?: number;
  fechaCambio: string;
}

export interface SolicitudRegistrarAsignacion {
  usuarioId: number;
  proyectoId: number;
  ubicacionId: number;
  horarioId: number;
  fechaInicio: string;
  fechaFin?: string | null;
}

export type TipoIncidencia = "TARDANZA" | "AUSENCIA" | "OLVIDO_REGISTRO" | "PERMISO" | "JUSTIFICACION";

export type EstadoIncidencia = "REGISTRADA" | "EN_REVISION" | "APROBADA" | "RECHAZADA" | "CERRADA";

export interface RespuestaIncidencia {
  id: number;
  usuarioId: number;
  nombreUsuario: string;
  marcacionId: number | null;
  tipo: TipoIncidencia;
  estado: EstadoIncidencia;
  fechaEvento: string;
  descripcion: string;
  creadoPorId: number;
  nombreCreadoPor: string;
  revisadoPorId: number | null;
  nombreRevisadoPor: string | null;
  comentarioRevision: string | null;
  revisadoEn: string | null;
  creadoEn: string;
  actualizadoEn: string;
}

export interface RespuestaHistorialIncidencia {
  estadoAnterior: EstadoIncidencia | null;
  estadoNuevo: EstadoIncidencia;
  usuarioId: number;
  nombreUsuario: string;
  comentario: string | null;
  creadoEn: string;
}

export interface RespuestaDetalleIncidencia {
  incidencia: RespuestaIncidencia;
  historial: RespuestaHistorialIncidencia[];
}

export interface SolicitudRegistrarIncidencia {
  usuarioId: number;
  tipo: TipoIncidencia;
  fechaEvento: string;
  descripcion: string;
}
