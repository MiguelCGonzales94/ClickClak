const ZONA_HORARIA = "America/Lima";

/** "2026-10-02" -> "02/10/2026". La fecha de un evento no lleva hora, así que no se convierte de zona. */
export function formatearFecha(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split("-");
  return `${dia}/${mes}/${anio}`;
}

/** Instante ISO (UTC) -> fecha y hora de Lima, que es donde trabaja el personal. */
export function formatearFechaHora(instanteIso: string): string {
  return new Date(instanteIso).toLocaleString("es-PE", {
    timeZone: ZONA_HORARIA,
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

/** Fecha de hoy en Lima como "AAAA-MM-DD" (formato del input type=date). */
export function hoyEnLima(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: ZONA_HORARIA }).format(ahora);
}
