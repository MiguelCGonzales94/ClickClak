import { clienteApi } from "./clienteApi";
import type { RespuestaUsuario, SolicitudEditarUsuario, SolicitudRegistrarUsuario } from "../types/api";

/** HU04: gestión de usuarios y roles. */
export const servicioUsuarios = {
  listar: (token: string, rol?: string, activo?: boolean) => {
    const parametros = new URLSearchParams();
    if (rol) parametros.set("rol", rol);
    if (activo !== undefined) parametros.set("activo", String(activo));
    const query = parametros.toString();
    return clienteApi.get<RespuestaUsuario[]>(`/api/usuarios${query ? `?${query}` : ""}`, token);
  },

  registrar: (token: string, solicitud: SolicitudRegistrarUsuario) =>
    clienteApi.post<RespuestaUsuario>("/api/usuarios", solicitud, token),

  editar: (token: string, id: number, solicitud: SolicitudEditarUsuario) =>
    clienteApi.put<RespuestaUsuario>(`/api/usuarios/${id}`, solicitud, token),

  activar: (token: string, id: number) =>
    clienteApi.post<RespuestaUsuario>(`/api/usuarios/${id}/activar`, undefined, token),

  desactivar: (token: string, id: number) =>
    clienteApi.post<RespuestaUsuario>(`/api/usuarios/${id}/desactivar`, undefined, token),
};
