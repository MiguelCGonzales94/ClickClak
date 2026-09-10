import { clienteApi } from "./clienteApi";
import type { RespuestaUbicacion, SolicitudRegistrarUbicacion } from "../types/api";

/** HU06: sedes y zonas autorizadas dentro de un proyecto. */
export const servicioUbicaciones = {
  listar: (token: string, proyectoId?: number) => {
    const query = proyectoId !== undefined ? `?proyectoId=${proyectoId}` : "";
    return clienteApi.get<RespuestaUbicacion[]>(`/api/ubicaciones${query}`, token);
  },

  registrar: (token: string, solicitud: SolicitudRegistrarUbicacion) =>
    clienteApi.post<RespuestaUbicacion>("/api/ubicaciones", solicitud, token),
};
