import { clienteApi } from "./clienteApi";
import type { RespuestaLogin, RespuestaPerfil, SolicitudLogin } from "../types/api";

export const servicioAutenticacion = {
  iniciarSesion: (solicitud: SolicitudLogin) =>
    clienteApi.post<RespuestaLogin>("/api/auth/login", solicitud),

  obtenerPerfil: (token: string) => clienteApi.get<RespuestaPerfil>("/api/auth/yo", token),

  /** HU02: revoca el token en el servidor — no basta con borrarlo del navegador. */
  cerrarSesion: (token: string) => clienteApi.post<void>("/api/auth/logout", undefined, token),
};
