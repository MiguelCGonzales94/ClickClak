import { clienteApi } from "./clienteApi";
import type { RespuestaAsignacion } from "../types/api";

/** HU09: agenda propia del colaborador autenticado. */
export const servicioAgenda = {
  obtenerMiAgenda: (token: string) =>
    clienteApi.get<RespuestaAsignacion[]>("/api/asignaciones/mias", token),
};
