import { clienteApi } from "./clienteApi";
import type {
  EstadoIncidencia,
  RespuestaDetalleIncidencia,
  RespuestaIncidencia,
  SolicitudRegistrarIncidencia,
} from "../types/api";

/** Gestión de incidencias laborales: alta por el personal de revisión y flujo de revisión. */
export const servicioIncidencias = {
  listar: (token: string, estado?: EstadoIncidencia, usuarioId?: number) => {
    const parametros = new URLSearchParams();
    if (estado) parametros.set("estado", estado);
    if (usuarioId !== undefined) parametros.set("usuarioId", String(usuarioId));
    const query = parametros.toString();
    return clienteApi.get<RespuestaIncidencia[]>(`/api/incidencias${query ? `?${query}` : ""}`, token);
  },

  obtener: (token: string, id: number) =>
    clienteApi.get<RespuestaDetalleIncidencia>(`/api/incidencias/${id}`, token),

  registrar: (token: string, solicitud: SolicitudRegistrarIncidencia) =>
    clienteApi.post<RespuestaIncidencia>("/api/incidencias", solicitud, token),

  iniciarRevision: (token: string, id: number) =>
    clienteApi.post<RespuestaIncidencia>(`/api/incidencias/${id}/iniciar-revision`, undefined, token),

  aprobar: (token: string, id: number, comentario?: string) =>
    clienteApi.post<RespuestaIncidencia>(`/api/incidencias/${id}/aprobar`, { comentario }, token),

  rechazar: (token: string, id: number, comentario: string) =>
    clienteApi.post<RespuestaIncidencia>(`/api/incidencias/${id}/rechazar`, { comentario }, token),

  cerrar: (token: string, id: number, comentario?: string) =>
    clienteApi.post<RespuestaIncidencia>(`/api/incidencias/${id}/cerrar`, { comentario }, token),
};
