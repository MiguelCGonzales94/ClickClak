import type { EstadoValidacion } from "../types/api";
import { clasesEstado, etiquetaEstado, explicacionEstado } from "./reglas";

/** Etiqueta del estado de validación; la explicación del motor sale como ayuda al pasar el cursor. */
export function PillEstadoValidacion({ estado }: { estado: EstadoValidacion }) {
  return (
    <span
      title={explicacionEstado(estado)}
      className={`text-xs font-semibold px-2 py-1 rounded-full whitespace-nowrap ${clasesEstado(estado)}`}
    >
      {etiquetaEstado(estado)}
    </span>
  );
}
