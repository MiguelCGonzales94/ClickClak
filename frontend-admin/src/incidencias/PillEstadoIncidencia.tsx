import type { EstadoIncidencia } from "../types/api";
import { ETIQUETA_ESTADO } from "./reglas";

const ESTILOS: Record<EstadoIncidencia, string> = {
  REGISTRADA: "bg-primario/10 text-primario",
  EN_REVISION: "bg-advertenciaFondo text-advertenciaTexto",
  APROBADA: "bg-exitoFondo text-exitoTexto",
  RECHAZADA: "bg-peligroFondo text-peligroTexto",
  CERRADA: "bg-borde text-textoSuave",
};

/** El color acompaña al texto del estado, nunca lo sustituye: no se depende solo del color para entenderlo. */
export function PillEstadoIncidencia({ estado }: { estado: EstadoIncidencia }) {
  return (
    <span className={`text-xs font-semibold px-2 py-1 rounded-full whitespace-nowrap ${ESTILOS[estado]}`}>
      {ETIQUETA_ESTADO[estado]}
    </span>
  );
}
