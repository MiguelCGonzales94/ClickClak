import { clienteApi } from "./clienteApi";
import type {
  RespuestaAsignacion,
  SolicitudEditarAsignacion,
  SolicitudMoverAsignacion,
  SolicitudRegistrarAsignacion,
} from "../types/api";

/** HU08: asignación de técnicos a proyectos/sedes/turnos. Un técnico puede tener varias sedes a la vez. */
export const servicioAsignaciones = {
  listar: (token: string, usuarioId?: number) => {
    const query = usuarioId !== undefined ? `?usuarioId=${usuarioId}` : "";
    return clienteApi.get<RespuestaAsignacion[]>(`/api/asignaciones${query}`, token);
  },

  registrar: (token: string, solicitud: SolicitudRegistrarAsignacion) =>
    clienteApi.post<RespuestaAsignacion>("/api/asignaciones", solicitud, token),

  editar: (token: string, id: number, solicitud: SolicitudEditarAsignacion) =>
    clienteApi.put<RespuestaAsignacion>(`/api/asignaciones/${id}`, solicitud, token),

  /** Baja lógica: las marcaciones que ya la usaron conservan su asignación. */
  quitar: (token: string, id: number) => clienteApi.delete<void>(`/api/asignaciones/${id}`, token),

  /** Termina la asignación el día anterior y crea la nueva en la otra sede; devuelve la nueva. */
  mover: (token: string, id: number, solicitud: SolicitudMoverAsignacion) =>
    clienteApi.post<RespuestaAsignacion>(`/api/asignaciones/${id}/mover`, solicitud, token),
};
