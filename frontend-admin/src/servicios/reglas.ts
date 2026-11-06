import { validarFechas } from "../asignaciones/reglas";
import type { RespuestaProyecto, SolicitudRegistrarProyecto } from "../types/api";

/** Reglas puras del formulario de servicios (proyectos), separadas para probarlas sin renderizar. */

export interface ValoresServicio {
  nombre: string;
  cliente: string;
  fechaInicio: string;
  fechaFin: string;
}

export function valoresServicioVacios(fechaInicio = ""): ValoresServicio {
  return { nombre: "", cliente: "", fechaInicio, fechaFin: "" };
}

export function valoresDeServicio(proyecto: RespuestaProyecto): ValoresServicio {
  return {
    nombre: proyecto.nombre,
    cliente: proyecto.cliente,
    fechaInicio: proyecto.fechaInicio,
    fechaFin: proyecto.fechaFin ?? "",
  };
}

/** Primer problema del formulario, o null si se puede guardar. */
export function validarServicio(valores: ValoresServicio): string | null {
  if (valores.nombre.trim() === "") return "Escribe el nombre del servicio.";
  if (valores.cliente.trim() === "") return "Escribe el cliente.";
  return validarFechas(valores.fechaInicio, valores.fechaFin);
}

export function aSolicitudServicio(valores: ValoresServicio): SolicitudRegistrarProyecto {
  return {
    nombre: valores.nombre.trim(),
    cliente: valores.cliente.trim(),
    fechaInicio: valores.fechaInicio,
    fechaFin: valores.fechaFin || null,
  };
}

export function hayCambiosServicio(proyecto: RespuestaProyecto, valores: ValoresServicio): boolean {
  return (
    valores.nombre.trim() !== proyecto.nombre ||
    valores.cliente.trim() !== proyecto.cliente ||
    valores.fechaInicio !== proyecto.fechaInicio ||
    valores.fechaFin !== (proyecto.fechaFin ?? "")
  );
}

/** Para los selectores de asignaciones: solo lo activo, más lo que la asignación ya usa (aunque se haya desactivado). */
export function soloActivos<T extends { id: number; activo: boolean }>(items: T[], idActual?: number): T[] {
  return items.filter((i) => i.activo || i.id === idActual);
}
