import type { ReactNode } from "react";

export function EstadoPill({ activo, textoActivo = "Activo", textoInactivo = "Inactivo" }: { activo: boolean; textoActivo?: string; textoInactivo?: string }) {
  return (
    <span className={`text-xs font-semibold px-2 py-1 rounded-full whitespace-nowrap ${activo ? "bg-exitoFondo text-exitoTexto" : "bg-peligroFondo text-peligroTexto"}`}>
      {activo ? textoActivo : textoInactivo}
    </span>
  );
}

export function Tabla({
  columnas,
  filas,
  cargando,
  vacio,
}: {
  columnas: string[];
  filas: ReactNode[][];
  cargando: boolean;
  vacio: string;
}) {
  return (
    <div className="bg-superficie rounded-md shadow-tarjeta overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs font-semibold text-textoSuave uppercase border-b border-borde">
            {columnas.map((columna) => (
              <th key={columna} className="px-4 py-3">
                {columna}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {cargando ? (
            <tr>
              <td colSpan={columnas.length} className="px-4 py-6 text-center text-textoSuave">
                Cargando…
              </td>
            </tr>
          ) : filas.length === 0 ? (
            <tr>
              <td colSpan={columnas.length} className="px-4 py-6 text-center text-textoSuave">
                {vacio}
              </td>
            </tr>
          ) : (
            filas.map((fila, indice) => (
              <tr key={indice} className="border-b border-borde last:border-0">
                {fila.map((celda, indiceCelda) => (
                  <td key={indiceCelda} className="px-4 py-3 text-texto">
                    {celda}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
