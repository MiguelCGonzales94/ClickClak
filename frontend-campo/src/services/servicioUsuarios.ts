import { clienteApi } from "./clienteApi";
import type { RespuestaUsuario } from "../types/api";

/** HU04: consulta de usuarios. Solo RRHH_ADMIN/SUPERVISOR pueden listar (ver backend). */
export const servicioUsuarios = {
  listar: (token: string, rol?: string, activo?: boolean) => {
    const parametros = new URLSearchParams();
    if (rol) parametros.set("rol", rol);
    if (activo !== undefined) parametros.set("activo", String(activo));
    const query = parametros.toString();
    return clienteApi.get<RespuestaUsuario[]>(`/api/usuarios${query ? `?${query}` : ""}`, token);
  },
};
