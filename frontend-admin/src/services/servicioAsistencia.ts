import { clienteApi } from "./clienteApi";
import type {
  FiltrosAsistencia,
  RespuestaAsistencia,
  RespuestaPagina,
  RespuestaResumenAsistencia,
} from "../types/api";

type FiltrosDeConsulta = Pick<FiltrosAsistencia, "usuarioId" | "ubicacionId" | "desde" | "hasta" | "tipoEvento">;

function parametrosComunes(filtros: FiltrosDeConsulta): URLSearchParams {
  const parametros = new URLSearchParams();
  if (filtros.usuarioId) parametros.set("usuarioId", filtros.usuarioId);
  if (filtros.ubicacionId) parametros.set("ubicacionId", filtros.ubicacionId);
  if (filtros.desde) parametros.set("desde", filtros.desde);
  if (filtros.hasta) parametros.set("hasta", filtros.hasta);
  if (filtros.tipoEvento) parametros.set("tipoEvento", filtros.tipoEvento);
  return parametros;
}

/** Consulta de asistencia para supervisión (SUPERVISOR y RRHH_ADMIN). */
export const servicioAsistencia = {
  listar: (token: string, filtros: FiltrosAsistencia) => {
    const parametros = parametrosComunes(filtros);
    if (filtros.estado) parametros.set("estado", filtros.estado);
    parametros.set("pagina", String(filtros.pagina));
    parametros.set("tamano", String(filtros.tamano));
    return clienteApi.get<RespuestaPagina<RespuestaAsistencia>>(`/api/marcaciones?${parametros}`, token);
  },

  /** El resumen reparte entre todos los estados: no recibe el filtro de estado. */
  resumen: (token: string, filtros: FiltrosDeConsulta) =>
    clienteApi.get<RespuestaResumenAsistencia>(`/api/marcaciones/resumen?${parametrosComunes(filtros)}`, token),
};
