import { clienteApi } from "./clienteApi";
import type { RespuestaAsignacion, SolicitudRegistrarAsignacion } from "../types/api";

/** HU08: asignación de técnicos a proyectos/sedes/turnos. */
export const servicioAsignaciones = {
  listar: (token: string, usuarioId?: number) => {
    const query = usuarioId !== undefined ? `?usuarioId=${usuarioId}` : "";
    return clienteApi.get<RespuestaAsignacion[]>(`/api/asignaciones${query}`, token);
  },

  registrar: (token: string, solicitud: SolicitudRegistrarAsignacion) =>
    clienteApi.post<RespuestaAsignacion>("/api/asignaciones", solicitud, token),
};
