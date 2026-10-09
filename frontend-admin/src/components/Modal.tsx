import { useEffect, type ReactNode } from "react";

interface Props {
  titulo: string;
  onCerrar: () => void;
  children: ReactNode;
  /** Ancho máximo del cuadro; el historial necesita más que una confirmación. */
  ancho?: "sm" | "md" | "lg";
}

const ANCHOS = { sm: "max-w-md", md: "max-w-xl", lg: "max-w-3xl" };

/** Cuadro de diálogo sobre un fondo atenuado. Se cierra con Escape o al pulsar fuera. */
export function Modal({ titulo, onCerrar, children, ancho = "sm" }: Props) {
  useEffect(() => {
    function alPulsarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") onCerrar();
    }
    window.addEventListener("keydown", alPulsarTecla);
    return () => window.removeEventListener("keydown", alPulsarTecla);
  }, [onCerrar]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4 py-6"
      onMouseDown={(evento) => {
        if (evento.target === evento.currentTarget) onCerrar();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        className={`w-full ${ANCHOS[ancho]} max-h-full overflow-y-auto rounded-md bg-superficie p-6 shadow-tarjeta`}
      >
        <div className="mb-4 flex items-start justify-between gap-4">
          <h2 className="text-lg font-bold text-texto">{titulo}</h2>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar"
            className="h-8 w-8 shrink-0 rounded-sm text-xl leading-none text-textoSuave hover:bg-fondo hover:text-texto"
          >
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
