import type { EstadoValidacion, FiltrosAsistencia, RespuestaAsistencia, TipoEvento } from "../types/api";

/** Reglas puras de la pantalla de asistencia, separadas para probarlas sin renderizar. */

export const TAMANO_PAGINA = 20;

/**
 * Las explicaciones salen de `MotorValidacionContextualService`. El umbral de 500 m es hoy un
 * parámetro de negocio "pendiente de calibrar", por eso se dice "hoy".
 */
export const ESTADOS_VALIDACION: { valor: EstadoValidacion; etiqueta: string; explicacion: string }[] = [
  {
    valor: "VALIDO",
    etiqueta: "Válido",
    explicacion: "Aun contando el error del GPS en su contra, la persona está dentro del radio de la sede.",
  },
  {
    valor: "OBSERVADO",
    etiqueta: "Observado",
    explicacion:
      "Según el margen de error del GPS podría estar dentro o fuera del radio. Se acepta y queda para revisión; nunca se rechaza solo por esto.",
  },
  {
    valor: "FUERA_DE_TOLERANCIA",
    etiqueta: "Fuera de tolerancia",
    explicacion: "Aun contando el error del GPS a su favor, está fuera del radio de la sede.",
  },
  {
    valor: "SOSPECHOSO",
    etiqueta: "Sospechoso",
    explicacion: "El GPS reportó una precisión peor que 500 m (hoy) o ninguna: la lectura no es confiable.",
  },
  {
    valor: "SIN_ASIGNACION",
    etiqueta: "Sin asignación",
    explicacion: "La persona no tenía una asignación vigente contra la cual validar la marcación.",
  },
];

export const TIPOS_EVENTO: { valor: TipoEvento; etiqueta: string }[] = [
  { valor: "ENTRADA", etiqueta: "Entrada" },
  { valor: "INICIO_REFRIGERIO", etiqueta: "Inicio de refrigerio" },
  { valor: "FIN_REFRIGERIO", etiqueta: "Fin de refrigerio" },
  { valor: "SALIDA", etiqueta: "Salida" },
];

export function etiquetaEstado(estado: EstadoValidacion): string {
  return ESTADOS_VALIDACION.find((e) => e.valor === estado)?.etiqueta ?? estado;
}

export function explicacionEstado(estado: EstadoValidacion): string {
  return ESTADOS_VALIDACION.find((e) => e.valor === estado)?.explicacion ?? "";
}

export function etiquetaTipo(tipo: TipoEvento): string {
  return TIPOS_EVENTO.find((t) => t.valor === tipo)?.etiqueta ?? tipo;
}

/** Verde para lo válido, ámbar para lo que pide revisión, rojo para lo que quedó fuera, gris sin asignación. */
export function clasesEstado(estado: EstadoValidacion): string {
  switch (estado) {
    case "VALIDO":
      return "bg-exitoFondo text-exitoTexto";
    case "OBSERVADO":
      return "bg-advertenciaFondo text-advertenciaTexto";
    case "FUERA_DE_TOLERANCIA":
    case "SOSPECHOSO":
      return "bg-peligroFondo text-peligroTexto";
    case "SIN_ASIGNACION":
      return "bg-fondo text-textoSuave border border-borde";
  }
}

/** "AAAA-MM-DD" más o menos `dias`, sin pasar por zonas horarias (se calcula en UTC sobre la fecha pura). */
export function sumarDias(fechaIso: string, dias: number): string {
  const [anio, mes, dia] = fechaIso.split("-").map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia + dias));
  return fecha.toISOString().slice(0, 10);
}

export type RangoRapido = "hoy" | "ayer" | "7dias";

/** Rango de fechas de un atajo, tomando `hoy` como el día de Lima. */
export function rangoRapido(rango: RangoRapido, hoy: string): { desde: string; hasta: string } {
  switch (rango) {
    case "hoy":
      return { desde: hoy, hasta: hoy };
    case "ayer": {
      const ayer = sumarDias(hoy, -1);
      return { desde: ayer, hasta: ayer };
    }
    case "7dias":
      return { desde: sumarDias(hoy, -6), hasta: hoy };
  }
}

export function filtrosIniciales(hoy: string): FiltrosAsistencia {
  return { usuarioId: "", ubicacionId: "", desde: hoy, hasta: hoy, estado: "", tipoEvento: "", pagina: 0, tamano: TAMANO_PAGINA };
}

/** Un filtro de fechas incoherente se avisa antes de pedirlo al servidor, que lo rechazaría con 400. */
export function validarRango(desde: string, hasta: string): string | null {
  if (desde && hasta && desde > hasta) return "La fecha inicial no puede ser posterior a la final";
  return null;
}

/** Cuánto tardó en llegar al servidor una marcación. Menos de un minuto se considera inmediata. */
export function describirRetraso(segundos: number): string {
  if (segundos < 60) return "Inmediata";
  const minutos = Math.floor(segundos / 60);
  if (minutos < 60) return `${minutos} min después`;
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  if (horas < 24) return resto === 0 ? `${horas} h después` : `${horas} h ${resto} min después`;
  const dias = Math.floor(horas / 24);
  return `${dias} ${dias === 1 ? "día" : "días"} después`;
}

/** Marcación registrada sin conexión y sincronizada más tarde (hoy: más de un minuto de diferencia). */
export function seSincronizoDespues(marcacion: Pick<RespuestaAsistencia, "retrasoSincronizacionSegundos">): boolean {
  return marcacion.retrasoSincronizacionSegundos >= 60;
}

export function formatearMetros(valor: number | null): string {
  if (valor === null || valor === undefined) return "—";
  return `${Math.round(valor).toLocaleString("es-PE")} m`;
}

/** Enlace a un mapa público con el punto marcado. Abre otro sitio: se usa con `rel="noopener noreferrer"`. */
export function urlMapa(latitud: number, longitud: number): string {
  return `https://www.openstreetmap.org/?mlat=${latitud}&mlon=${longitud}#map=18/${latitud}/${longitud}`;
}

/** Cantidad de marcaciones que piden atención de supervisión: todo lo que no es válido ni sin asignación. */
export function totalPorRevisar(porEstado: Partial<Record<EstadoValidacion, number>>): number {
  return (porEstado.OBSERVADO ?? 0) + (porEstado.FUERA_DE_TOLERANCIA ?? 0) + (porEstado.SOSPECHOSO ?? 0);
}
