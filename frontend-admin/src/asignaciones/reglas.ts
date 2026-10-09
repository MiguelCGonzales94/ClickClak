import { sumarDias } from "../asistencia/reglas";
import type {
  EstadoAsignacion,
  RespuestaAsignacion,
  RespuestaUbicacion,
  SolicitudEditarAsignacion,
} from "../types/api";

/** Reglas puras de la pantalla de asignaciones, separadas para probarlas sin renderizar. */

export const ESTADOS_ASIGNACION: { valor: EstadoAsignacion; etiqueta: string }[] = [
  { valor: "VIGENTE", etiqueta: "Vigente" },
  { valor: "PROGRAMADA", etiqueta: "Programada" },
  { valor: "FINALIZADA", etiqueta: "Finalizada" },
];

export function etiquetaEstado(estado: EstadoAsignacion): string {
  return ESTADOS_ASIGNACION.find((e) => e.valor === estado)?.etiqueta ?? estado;
}

export function clasesEstado(estado: EstadoAsignacion): string {
  switch (estado) {
    case "VIGENTE":
      return "bg-exitoFondo text-exitoTexto";
    case "PROGRAMADA":
      return "bg-primario/10 text-primario";
    case "FINALIZADA":
      return "bg-fondo text-textoSuave border border-borde";
  }
}

/** Qué se muestra por defecto: lo que está en curso o por venir. Lo terminado se pide expresamente. */
export type FiltroEstado = "ACTIVAS" | EstadoAsignacion | "TODAS";

export function coincideConFiltro(asignacion: RespuestaAsignacion, filtro: FiltroEstado): boolean {
  switch (filtro) {
    case "TODAS":
      return true;
    case "ACTIVAS":
      return asignacion.estado !== "FINALIZADA";
    default:
      return asignacion.estado === filtro;
  }
}

export function filtrarAsignaciones(
  asignaciones: RespuestaAsignacion[],
  filtros: { usuarioId: string; estado: FiltroEstado },
): RespuestaAsignacion[] {
  return asignaciones.filter(
    (a) => (filtros.usuarioId === "" || String(a.usuarioId) === filtros.usuarioId) && coincideConFiltro(a, filtros.estado),
  );
}

/** Cuántas sedes tiene vigentes hoy cada técnico (id de usuario → cantidad), para avisar de quien tiene varias. */
export function sedesVigentesPorTecnico(asignaciones: RespuestaAsignacion[]): Record<number, number> {
  const cuenta: Record<number, number> = {};
  for (const a of asignaciones) {
    if (a.estado === "VIGENTE") cuenta[a.usuarioId] = (cuenta[a.usuarioId] ?? 0) + 1;
  }
  return cuenta;
}

/** Las sedes de un servicio, que son las únicas que se pueden elegir para él. */
export function sedesDelServicio(ubicaciones: RespuestaUbicacion[], proyectoId: string | number): RespuestaUbicacion[] {
  return ubicaciones.filter((u) => String(u.proyectoId) === String(proyectoId));
}

/** Fechas coherentes: un fin anterior al inicio se avisa antes de pedirlo al servidor, que respondería 400. */
export function validarFechas(inicio: string, fin: string): string | null {
  if (!inicio) return "Indique la fecha de inicio";
  if (fin && fin < inicio) return "La fecha de fin no puede ser anterior a la fecha de inicio";
  return null;
}

export interface Acciones {
  editar: boolean;
  mover: boolean;
  quitar: boolean;
}

/** Una asignación terminada solo se corrige o se quita: moverla no tiene sentido porque ya no está en curso. */
export function accionesDisponibles(asignacion: RespuestaAsignacion): Acciones {
  return { editar: true, mover: asignacion.estado !== "FINALIZADA", quitar: true };
}

/** Primer día posible del traslado: el siguiente al inicio de la asignación (el servidor exige uno posterior). */
export function fechaMinimaDeCambio(asignacion: Pick<RespuestaAsignacion, "fechaInicio">): string {
  return sumarDias(asignacion.fechaInicio, 1);
}

/** Último día posible del traslado: el fin de la asignación, si lo tiene (después ya habría terminado). */
export function fechaMaximaDeCambio(asignacion: Pick<RespuestaAsignacion, "fechaFin">): string | undefined {
  return asignacion.fechaFin ?? undefined;
}

/** Hoy si cae dentro del rango permitido; si no, el primer día posible. */
export function fechaDeCambioInicial(asignacion: RespuestaAsignacion, hoy: string): string {
  const minima = fechaMinimaDeCambio(asignacion);
  const maxima = fechaMaximaDeCambio(asignacion);
  if (hoy < minima) return minima;
  if (maxima && hoy > maxima) return maxima;
  return hoy;
}

export function validarFechaDeCambio(asignacion: RespuestaAsignacion, fechaCambio: string): string | null {
  if (!fechaCambio) return "Indique la fecha de cambio";
  if (fechaCambio < fechaMinimaDeCambio(asignacion)) {
    return `La fecha de cambio debe ser posterior al inicio de la asignación (${asignacion.fechaInicio})`;
  }
  const maxima = fechaMaximaDeCambio(asignacion);
  if (maxima && fechaCambio > maxima) return `La asignación actual termina el ${maxima}, antes de esa fecha`;
  return null;
}

/** Texto que explica qué hará el traslado, para que nadie lo ejecute sin entenderlo. */
export function describirTraslado(asignacion: RespuestaAsignacion, fechaCambio: string, nombreSedeNueva: string): string {
  const fin = asignacion.fechaFin ? `hasta el ${asignacion.fechaFin}` : "sin fecha de término";
  return (
    `${asignacion.nombreUbicacion} termina el ${sumarDias(fechaCambio, -1)} y ` +
    `${nombreSedeNueva || "la sede nueva"} empieza el ${fechaCambio}, ${fin}.`
  );
}

/** ¿Cambió algo respecto de la asignación original? Evita guardar una edición vacía. */
export function hayCambios(original: RespuestaAsignacion, nueva: SolicitudEditarAsignacion): boolean {
  return (
    original.proyectoId !== nueva.proyectoId ||
    original.ubicacionId !== nueva.ubicacionId ||
    original.horarioId !== nueva.horarioId ||
    original.fechaInicio !== nueva.fechaInicio ||
    (original.fechaFin ?? null) !== (nueva.fechaFin ?? null)
  );
}
