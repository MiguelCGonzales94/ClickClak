import { clienteApi } from "./clienteApi";
import type { RespuestaProyecto, SolicitudRegistrarProyecto } from "../types/api";

/** HU06: proyectos (servicios/clientes) que agrupan las sedes autorizadas. */
export const servicioProyectos = {
  listar: (token: string) => clienteApi.get<RespuestaProyecto[]>("/api/proyectos", token),

  registrar: (token: string, solicitud: SolicitudRegistrarProyecto) =>
    clienteApi.post<RespuestaProyecto>("/api/proyectos", solicitud, token),

  /** Corrige nombre, cliente y fechas. */
  editar: (token: string, id: number, solicitud: SolicitudRegistrarProyecto) =>
    clienteApi.put<RespuestaProyecto>(`/api/proyectos/${id}`, solicitud, token),

  /** Baja lógica; el servidor responde 409 si hay asignaciones en curso o programadas. */
  desactivar: (token: string, id: number) =>
    clienteApi.post<RespuestaProyecto>(`/api/proyectos/${id}/desactivar`, {}, token),

  reactivar: (token: string, id: number) => clienteApi.post<RespuestaProyecto>(`/api/proyectos/${id}/reactivar`, {}, token),

  /** Borrado físico, solo si nunca tuvo sedes ni asignaciones; si no, 409. */
  eliminar: (token: string, id: number) => clienteApi.delete<void>(`/api/proyectos/${id}`, token),
};
