import { clienteApi } from "./clienteApi";
import type {
  RespuestaLogin,
  RespuestaMensaje,
  RespuestaPerfil,
  SolicitudCambiarClave,
  SolicitudLogin,
} from "../types/api";

export const servicioAutenticacion = {
  iniciarSesion: (solicitud: SolicitudLogin) =>
    clienteApi.post<RespuestaLogin>("/api/auth/login", solicitud),

  obtenerPerfil: (token: string) => clienteApi.get<RespuestaPerfil>("/api/auth/yo", token),

  /** HU02: revoca el token en el servidor — no basta con borrarlo del navegador. */
  cerrarSesion: (token: string) => clienteApi.post<void>("/api/auth/logout", undefined, token),

  /** HU04: cambio de la propia clave. El servidor revoca el token usado: hay que iniciar sesión de nuevo. */
  cambiarClave: (token: string, solicitud: SolicitudCambiarClave) =>
    clienteApi.post<RespuestaMensaje>("/api/auth/cambiar-clave", solicitud, token),
};
