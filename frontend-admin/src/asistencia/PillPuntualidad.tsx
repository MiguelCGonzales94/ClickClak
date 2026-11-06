import type { RespuestaAsistencia } from "../types/api";
import { clasesPuntualidad, describirPuntualidadCorta, etiquetaPuntualidad, explicarPuntualidad } from "./puntualidad";

/**
 * Cómo llegó la persona respecto de su turno. Solo las entradas con turno tienen puntualidad; en el resto
 * (salidas, refrigerios, marcaciones sin asignación) se muestra un guion. La explicación sale al pasar el cursor.
 */
export function PillPuntualidad({ marcacion }: { marcacion: RespuestaAsistencia }) {
  if (marcacion.puntualidad === null || marcacion.minutosRespectoAlInicio === null) {
    return <span className="text-textoSuave">—</span>;
  }
  return (
    <div title={explicarPuntualidad(marcacion) ?? undefined} className="flex flex-col items-start gap-0.5">
      <span className={`whitespace-nowrap rounded-full px-2 py-1 text-xs font-semibold ${clasesPuntualidad(marcacion.puntualidad)}`}>
        {etiquetaPuntualidad(marcacion.puntualidad)}
      </span>
      <span className="whitespace-nowrap text-xs text-textoSuave">
        {describirPuntualidadCorta(marcacion.puntualidad, marcacion.minutosRespectoAlInicio)}
      </span>
    </div>
  );
}
