import type { EstadoIncidencia, RespuestaIncidencia, TipoIncidencia } from "../types/api";
import { hoyEnLima } from "../utilidades/fechas";

/**
 * Reglas del flujo de incidencias que la pantalla necesita para mostrar solo lo que procede.
 * Reflejan las del backend (IncidenciaService), que es quien las impone de verdad: aquí solo evitan
 * ofrecer botones que el servidor rechazaría.
 */

export const ESTADOS: EstadoIncidencia[] = ["REGISTRADA", "EN_REVISION", "APROBADA", "RECHAZADA", "CERRADA"];
export const TIPOS: TipoIncidencia[] = ["TARDANZA", "AUSENCIA", "OLVIDO_REGISTRO", "PERMISO", "JUSTIFICACION"];

export const ETIQUETA_ESTADO: Record<EstadoIncidencia, string> = {
  REGISTRADA: "Registrada",
  EN_REVISION: "En revisión",
  APROBADA: "Aprobada",
  RECHAZADA: "Rechazada",
  CERRADA: "Cerrada",
};

export const ETIQUETA_TIPO: Record<TipoIncidencia, string> = {
  TARDANZA: "Tardanza",
  AUSENCIA: "Ausencia",
  OLVIDO_REGISTRO: "Olvido de registro",
  PERMISO: "Permiso",
  JUSTIFICACION: "Justificación",
};

export type AccionIncidencia = "iniciarRevision" | "aprobar" | "rechazar" | "cerrar";

const ACCIONES_POR_ESTADO: Record<EstadoIncidencia, AccionIncidencia[]> = {
  REGISTRADA: ["iniciarRevision"],
  EN_REVISION: ["aprobar", "rechazar"],
  APROBADA: ["cerrar"],
  RECHAZADA: ["cerrar"],
  CERRADA: [],
};

/** Acciones que el flujo permite desde un estado: Registrada → En revisión → Aprobada/Rechazada → Cerrada. */
export function accionesDisponibles(estado: EstadoIncidencia): AccionIncidencia[] {
  return ACCIONES_POR_ESTADO[estado];
}

/** Cerrar es administrativo: la separación de funciones solo aplica a revisar y resolver. */
export function esAccionDeRevision(accion: AccionIncidencia): boolean {
  return accion !== "cerrar";
}

/** Rechazar exige indicar el motivo. */
export function requiereComentario(accion: AccionIncidencia): boolean {
  return accion === "rechazar";
}

/** Separación de funciones: nadie revisa una incidencia que le afecta o que él mismo registró. */
export function motivoDeBloqueo(
  accion: AccionIncidencia,
  incidencia: Pick<RespuestaIncidencia, "usuarioId" | "creadoPorId">,
  perfilId: number,
): string | null {
  if (!esAccionDeRevision(accion)) return null;
  if (incidencia.usuarioId === perfilId || incidencia.creadoPorId === perfilId) {
    return "No puede revisar una incidencia que le afecta o que usted mismo registró. Debe resolverla otra persona.";
  }
  return null;
}

/** Un permiso se puede pedir por adelantado; cualquier otro tipo describe algo que ya ocurrió. */
export function fechaMaximaDeEvento(tipo: TipoIncidencia, hoy: string = hoyEnLima()): string | undefined {
  return tipo === "PERMISO" ? undefined : hoy;
}

export function esPendienteDeRevision(estado: EstadoIncidencia): boolean {
  return estado === "REGISTRADA" || estado === "EN_REVISION";
}

export function contarPorEstado(incidencias: Pick<RespuestaIncidencia, "estado">[]): Record<EstadoIncidencia, number> {
  const conteo: Record<EstadoIncidencia, number> = {
    REGISTRADA: 0,
    EN_REVISION: 0,
    APROBADA: 0,
    RECHAZADA: 0,
    CERRADA: 0,
  };
  for (const incidencia of incidencias) {
    conteo[incidencia.estado] += 1;
  }
  return conteo;
}

function sinTildes(texto: string): string {
  return texto.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}

/** Filtra por estado y por un texto que se busca, sin distinguir tildes ni mayúsculas, en nombre, tipo y descripción. */
export function filtrarIncidencias(
  incidencias: RespuestaIncidencia[],
  { estado, texto }: { estado?: EstadoIncidencia | null; texto?: string },
): RespuestaIncidencia[] {
  const buscado = sinTildes((texto ?? "").trim());
  return incidencias.filter((incidencia) => {
    if (estado && incidencia.estado !== estado) return false;
    if (!buscado) return true;
    const pajar = sinTildes(`${incidencia.nombreUsuario} ${ETIQUETA_TIPO[incidencia.tipo]} ${incidencia.descripcion}`);
    return pajar.includes(buscado);
  });
}
