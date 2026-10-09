import { useEffect, useState } from "react";
import { ErrorHttp } from "../services/clienteApi";
import { servicioUsuarios } from "../services/servicioUsuarios";
import type { RespuestaHistorialUsuario } from "../types/api";
import { formatearFechaHora } from "../utilidades/fechas";
import { describirEntradaHistorial } from "./reglas";

const ETIQUETA_ACCION: Record<RespuestaHistorialUsuario["accion"], string> = {
  CREACION: "Alta",
  MODIFICACION: "Cambio",
  ELIMINACION: "Eliminación",
};

/** HU04: línea de tiempo de una cuenta, de lo más reciente a lo más antiguo, tomada de la bitácora. */
export function PanelHistorialUsuario({ token, usuarioId }: { token: string; usuarioId: number }) {
  const [entradas, setEntradas] = useState<RespuestaHistorialUsuario[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let vigente = true;
    servicioUsuarios
      .historial(token, usuarioId)
      .then((resultado) => vigente && setEntradas(resultado))
      .catch((err) => vigente && setError(err instanceof ErrorHttp ? err.message : "No se pudo cargar el historial"));
    return () => {
      vigente = false;
    };
  }, [token, usuarioId]);

  if (error) {
    return (
      <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
        {error}
      </p>
    );
  }
  if (!entradas) {
    return <p className="text-sm text-textoSuave">Cargando…</p>;
  }
  if (entradas.length === 0) {
    return <p className="text-sm text-textoSuave">Esta cuenta no tiene movimientos registrados.</p>;
  }

  return (
    <ol className="flex flex-col gap-3">
      {entradas.map((entrada) => (
        <li key={entrada.id} className="rounded-sm border border-borde px-4 py-3">
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-textoSuave">
            <span className="font-semibold uppercase">{ETIQUETA_ACCION[entrada.accion]}</span>
            <span>
              {formatearFechaHora(entrada.creadoEn)} · {entrada.actorNombre ?? "Sistema"}
            </span>
          </div>
          <p className="mt-1 text-sm text-texto">{describirEntradaHistorial(entrada)}</p>
        </li>
      ))}
    </ol>
  );
}
