import { clienteApi } from "./clienteApi";
import type {
  RespuestaLogin,
  RespuestaMensaje,
  RespuestaPerfil,
  SolicitudLogin,
  SolicitudRestablecerClave,
  SolicitudSolicitarRecuperacion,
} from "../types/api";

export const servicioAutenticacion = {
  iniciarSesion: (solicitud: SolicitudLogin) =>
    clienteApi.post<RespuestaLogin>("/api/auth/login", solicitud),

  obtenerPerfil: (token: string) => clienteApi.get<RespuestaPerfil>("/api/auth/yo", token),

  /** HU02: revoca el token en el servidor — no basta con borrarlo del navegador. */
  cerrarSesion: (token: string) => clienteApi.post<void>("/api/auth/logout", undefined, token),

  /** HU03. La respuesta es siempre el mismo mensaje genérico, exista o no el correo. */
  solicitarRecuperacion: (solicitud: SolicitudSolicitarRecuperacion) =>
    clienteApi.post<RespuestaMensaje>("/api/auth/recuperacion/solicitar", solicitud),

  restablecerClave: (solicitud: SolicitudRestablecerClave) =>
    clienteApi.post<RespuestaMensaje>("/api/auth/recuperacion/restablecer", solicitud),
};
