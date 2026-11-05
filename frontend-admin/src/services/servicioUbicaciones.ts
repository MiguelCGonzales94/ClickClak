import { clienteApi } from "./clienteApi";
import type { RespuestaUbicacion, SolicitudEditarUbicacion, SolicitudRegistrarUbicacion } from "../types/api";

/** HU06: sedes y zonas autorizadas dentro de un proyecto. */
export const servicioUbicaciones = {
  listar: (token: string, proyectoId?: number) => {
    const query = proyectoId !== undefined ? `?proyectoId=${proyectoId}` : "";
    return clienteApi.get<RespuestaUbicacion[]>(`/api/ubicaciones${query}`, token);
  },

  registrar: (token: string, solicitud: SolicitudRegistrarUbicacion) =>
    clienteApi.post<RespuestaUbicacion>("/api/ubicaciones", solicitud, token),

  /** Corrige nombre, referencia y zona (centro y radio); el servicio no cambia. */
  editar: (token: string, id: number, solicitud: SolicitudEditarUbicacion) =>
    clienteApi.put<RespuestaUbicacion>(`/api/ubicaciones/${id}`, solicitud, token),

  /** Baja lógica; el servidor responde 409 si la sede tiene asignaciones en curso o programadas. */
  desactivar: (token: string, id: number) => clienteApi.delete<RespuestaUbicacion>(`/api/ubicaciones/${id}`, token),

  reactivar: (token: string, id: number) =>
    clienteApi.post<RespuestaUbicacion>(`/api/ubicaciones/${id}/reactivar`, {}, token),
};
