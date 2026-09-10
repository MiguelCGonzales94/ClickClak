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

export interface ErrorApi {
  error: string;
}

export interface SolicitudIniciarRegistroWebAuthn {
  usuarioId: number;
}

export interface SolicitudFinalizarRegistroWebAuthn {
  idSolicitud: string;
  credencialJson: string;
  nombreDispositivo?: string;
}

export interface SolicitudIniciarAutenticacionWebAuthn {
  correo: string;
}

export interface SolicitudFinalizarAutenticacionWebAuthn {
  idSolicitud: string;
  credencialJson: string;
}

/** {@code opcionesJson} ya viene envuelto como {"publicKey": {...}}, listo para parseCreationOptionsFromJSON/parseRequestOptionsFromJSON. */
export interface RespuestaOpcionesWebAuthn {
  idSolicitud: string;
  opcionesJson: string;
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

export interface SolicitudSolicitarRecuperacion {
  correo: string;
}

export interface SolicitudRestablecerClave {
  token: string;
  nuevaPassword: string;
}

export interface RespuestaMensaje {
  mensaje: string;
}
