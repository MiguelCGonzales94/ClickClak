import type { EstadoPuntualidad, RespuestaAsistencia } from "../types/api";

/** Reglas puras de la columna de puntualidad: cómo llegó una persona respecto del inicio de su turno. */

export const ESTADOS_PUNTUALIDAD: { valor: EstadoPuntualidad; etiqueta: string }[] = [
  { valor: "TEMPRANO", etiqueta: "Temprano" },
  { valor: "DENTRO_DE_TOLERANCIA", etiqueta: "En tolerancia" },
  { valor: "TARDE", etiqueta: "Tarde" },
];

export function etiquetaPuntualidad(estado: EstadoPuntualidad): string {
  return ESTADOS_PUNTUALIDAD.find((e) => e.valor === estado)?.etiqueta ?? estado;
}

export function clasesPuntualidad(estado: EstadoPuntualidad): string {
  switch (estado) {
    case "TEMPRANO":
      return "bg-primario/10 text-primario";
    case "DENTRO_DE_TOLERANCIA":
      return "bg-exitoFondo text-exitoTexto";
    case "TARDE":
      return "bg-peligroFondo text-peligroTexto";
  }
}

function minutos(n: number): string {
  return n === 1 ? "1 minuto" : `${n} minutos`;
}

/** «15 min antes», «A la hora», «+5 min» o «11 min tarde»: lo breve que cabe en la tabla. */
export function describirPuntualidadCorta(estado: EstadoPuntualidad, minutosRespectoAlInicio: number): string {
  switch (estado) {
    case "TEMPRANO":
      return `${Math.abs(minutosRespectoAlInicio)} min antes`;
    case "DENTRO_DE_TOLERANCIA":
      return minutosRespectoAlInicio === 0 ? "A la hora" : `+${minutosRespectoAlInicio} min`;
    case "TARDE":
      return `${minutosRespectoAlInicio} min tarde`;
  }
}

/** La misma regla dicha completa, con el turno y su tolerancia, para el detalle y la ayuda al pasar el cursor. */
export function explicarPuntualidad(m: Pick<RespuestaAsistencia, "puntualidad" | "minutosRespectoAlInicio" | "horaInicioTurno" | "toleranciaTurnoMinutos">): string | null {
  if (m.puntualidad === null || m.minutosRespectoAlInicio === null || m.horaInicioTurno === null || m.toleranciaTurnoMinutos === null) {
    return null;
  }
  const turno = `El turno empieza a las ${m.horaInicioTurno} y tiene ${minutos(m.toleranciaTurnoMinutos)} de tolerancia.`;
  switch (m.puntualidad) {
    case "TEMPRANO":
      return `${turno} Llegó ${minutos(Math.abs(m.minutosRespectoAlInicio))} antes.`;
    case "DENTRO_DE_TOLERANCIA":
      return m.minutosRespectoAlInicio === 0
        ? `${turno} Llegó a la hora.`
        : `${turno} Llegó ${minutos(m.minutosRespectoAlInicio)} después de la hora, dentro de la tolerancia.`;
    case "TARDE":
      return `${turno} Llegó ${minutos(m.minutosRespectoAlInicio)} después de la hora: pasó la tolerancia y cuenta como tardanza.`;
  }
}

/** Cuántas entradas llegaron tarde en la lista visible, para el aviso sobre la tabla. */
export function contarTardes(marcaciones: RespuestaAsistencia[]): number {
  return marcaciones.filter((m) => m.puntualidad === "TARDE").length;
}
