import { clienteApi } from "./clienteApi";
import type { RespuestaHorario, SolicitudRegistrarHorario } from "../types/api";

/** HU07: turnos y horarios de trabajo del personal de campo. */
export const servicioHorarios = {
  listar: (token: string) => clienteApi.get<RespuestaHorario[]>("/api/horarios", token),

  registrar: (token: string, solicitud: SolicitudRegistrarHorario) =>
    clienteApi.post<RespuestaHorario>("/api/horarios", solicitud, token),
};
