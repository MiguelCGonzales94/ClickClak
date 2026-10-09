import type { EstadoCuenta } from "../types/api";
import { clasesEstado, etiquetaEstado } from "./reglas";

/** Etiqueta del estado de cuenta; `detalle` (p. ej. el motivo de baja) sale como ayuda al pasar el cursor. */
export function PillEstadoCuenta({ estado, detalle }: { estado: EstadoCuenta; detalle?: string | null }) {
  return (
    <span
      title={detalle ?? undefined}
      className={`text-xs font-semibold px-2 py-1 rounded-full whitespace-nowrap ${clasesEstado(estado)}`}
    >
      {etiquetaEstado(estado)}
    </span>
  );
}
