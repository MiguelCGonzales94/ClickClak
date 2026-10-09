import { clienteApi } from "./clienteApi";
import type {
  FiltrosUsuarios,
  RespuestaClaveTemporal,
  RespuestaHistorialUsuario,
  RespuestaPagina,
  RespuestaUsuario,
  SolicitudEditarUsuario,
  SolicitudRegistrarUsuario,
} from "../types/api";

/** HU04: gestión de usuarios y roles. */
export const servicioUsuarios = {
  /** Lista completa sin paginar: la usan otras pantallas para elegir técnicos. */
  listar: (token: string, rol?: string, activo?: boolean) => {
    const parametros = new URLSearchParams();
    if (rol) parametros.set("rol", rol);
    if (activo !== undefined) parametros.set("activo", String(activo));
    const query = parametros.toString();
    return clienteApi.get<RespuestaUsuario[]>(`/api/usuarios${query ? `?${query}` : ""}`, token);
  },

  /** Búsqueda paginada del panel de administración (solo RRHH_ADMIN). */
  buscar: (token: string, filtros: FiltrosUsuarios) => {
    const parametros = new URLSearchParams();
    if (filtros.texto.trim()) parametros.set("q", filtros.texto.trim());
    if (filtros.rol) parametros.set("rol", filtros.rol);
    if (filtros.estado) parametros.set("estado", filtros.estado);
    parametros.set("pagina", String(filtros.pagina));
    parametros.set("tamano", String(filtros.tamano));
    return clienteApi.get<RespuestaPagina<RespuestaUsuario>>(`/api/usuarios/buscar?${parametros}`, token);
  },

  registrar: (token: string, solicitud: SolicitudRegistrarUsuario) =>
    clienteApi.post<RespuestaUsuario>("/api/usuarios", solicitud, token),

  editar: (token: string, id: number, solicitud: SolicitudEditarUsuario) =>
    clienteApi.put<RespuestaUsuario>(`/api/usuarios/${id}`, solicitud, token),

  activar: (token: string, id: number) =>
    clienteApi.post<RespuestaUsuario>(`/api/usuarios/${id}/activar`, undefined, token),

  /** El motivo es opcional y queda en el usuario y en el historial. */
  desactivar: (token: string, id: number, motivo?: string) =>
    clienteApi.post<RespuestaUsuario>(
      `/api/usuarios/${id}/desactivar`,
      motivo?.trim() ? { motivo: motivo.trim() } : undefined,
      token,
    ),

  desbloquear: (token: string, id: number) =>
    clienteApi.post<RespuestaUsuario>(`/api/usuarios/${id}/desbloquear`, undefined, token),

  /** La clave temporal solo llega en esta respuesta: si se pierde hay que restablecer de nuevo. */
  restablecerClave: (token: string, id: number) =>
    clienteApi.post<RespuestaClaveTemporal>(`/api/usuarios/${id}/restablecer-clave`, undefined, token),

  /** Solo borra a quien no tiene historial; si lo tiene, el servidor responde 409. */
  eliminar: (token: string, id: number) => clienteApi.delete<void>(`/api/usuarios/${id}`, token),

  historial: (token: string, id: number) =>
    clienteApi.get<RespuestaHistorialUsuario[]>(`/api/usuarios/${id}/historial`, token),
};
