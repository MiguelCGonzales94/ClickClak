import { clienteApi } from "./clienteApi";
import type { RespuestaMarcacion, SolicitudRegistrarMarcacion } from "../types/api";

/** HU10/HU11: registro de asistencia geolocalizada contra el backend. */
export const servicioMarcaciones = {
  registrar: (token: string, solicitud: SolicitudRegistrarMarcacion) =>
    clienteApi.post<RespuestaMarcacion>("/api/marcaciones", solicitud, token),
};
