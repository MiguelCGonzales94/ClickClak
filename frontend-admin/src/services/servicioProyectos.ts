import { clienteApi } from "./clienteApi";
import type { RespuestaProyecto, SolicitudRegistrarProyecto } from "../types/api";

/** HU06: proyectos (servicios/clientes) que agrupan las sedes autorizadas. */
export const servicioProyectos = {
  listar: (token: string) => clienteApi.get<RespuestaProyecto[]>("/api/proyectos", token),

  registrar: (token: string, solicitud: SolicitudRegistrarProyecto) =>
    clienteApi.post<RespuestaProyecto>("/api/proyectos", solicitud, token),
};
