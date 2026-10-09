import { useEffect, useRef, useState } from "react";

export interface OpcionMenu {
  etiqueta: string;
  alElegir: () => void;
  /** Resalta la opción destructiva (eliminar, desactivar). */
  peligro?: boolean;
}

const ALTO_OPCION = 36;

interface Posicion {
  derecha: number;
  arriba?: number;
  abajo?: number;
}

/**
 * Menú desplegable de acciones de una fila. Se coloca con `position: fixed` porque la tabla tiene
 * `overflow-x-auto`, que recortaría un menú absoluto en las últimas filas; abre hacia arriba si
 * abajo no cabe. Se cierra al elegir, con Escape, al pulsar fuera o al desplazar la página.
 */
export function MenuAcciones({ opciones, etiqueta }: { opciones: OpcionMenu[]; etiqueta: string }) {
  const [posicion, setPosicion] = useState<Posicion | null>(null);
  const contenedor = useRef<HTMLDivElement>(null);
  const abierto = posicion !== null;

  useEffect(() => {
    if (!abierto) return;
    const cerrar = () => setPosicion(null);
    function alPulsarFuera(evento: MouseEvent) {
      if (contenedor.current && !contenedor.current.contains(evento.target as Node)) cerrar();
    }
    function alPulsarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") cerrar();
    }
    document.addEventListener("mousedown", alPulsarFuera);
    document.addEventListener("keydown", alPulsarTecla);
    window.addEventListener("scroll", cerrar, true);
    window.addEventListener("resize", cerrar);
    return () => {
      document.removeEventListener("mousedown", alPulsarFuera);
      document.removeEventListener("keydown", alPulsarTecla);
      window.removeEventListener("scroll", cerrar, true);
      window.removeEventListener("resize", cerrar);
    };
  }, [abierto]);

  function alternar(evento: React.MouseEvent<HTMLButtonElement>) {
    if (abierto) {
      setPosicion(null);
      return;
    }
    const caja = evento.currentTarget.getBoundingClientRect();
    const altoMenu = opciones.length * ALTO_OPCION + 8;
    const derecha = window.innerWidth - caja.right;
    setPosicion(
      caja.bottom + altoMenu > window.innerHeight
        ? { derecha, abajo: window.innerHeight - caja.top + 4 }
        : { derecha, arriba: caja.bottom + 4 },
    );
  }

  return (
    <div ref={contenedor} className="inline-block text-left">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={abierto}
        aria-label={etiqueta}
        onClick={alternar}
        className="h-8 px-3 rounded-sm border border-borde text-xs font-semibold text-texto hover:bg-fondo"
      >
        Acciones ▾
      </button>
      {posicion && (
        <ul
          role="menu"
          style={{ position: "fixed", right: posicion.derecha, top: posicion.arriba, bottom: posicion.abajo }}
          className="z-40 w-52 overflow-hidden rounded-sm border border-borde bg-superficie py-1 shadow-tarjeta"
        >
          {opciones.map((opcion) => (
            <li key={opcion.etiqueta} role="none">
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setPosicion(null);
                  opcion.alElegir();
                }}
                className={`block w-full px-3 py-2 text-left text-sm hover:bg-fondo ${
                  opcion.peligro ? "text-peligroTexto" : "text-texto"
                }`}
              >
                {opcion.etiqueta}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
