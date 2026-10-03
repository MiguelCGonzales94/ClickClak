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
}

export interface RespuestaPerfil {
  id: number;
  nombres: string;
  apellidos: string;
  correo: string;
  rol: string;
}

export interface ErrorApi {
  error: string;
}

export interface RespuestaUsuario {
  id: number;
  nombres: string;
  apellidos: string;
  tipoDocumento: string;
  numeroDocumento: string;
  correo: string;
  rol: string;
  activo: boolean;
  creadoEn: string;
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
  estado: "VIGENTE" | "PROGRAMADA";
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
