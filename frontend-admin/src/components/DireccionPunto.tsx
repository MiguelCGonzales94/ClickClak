import { useEffect, useState } from "react";
import { clienteApi } from "../services/clienteApi";
import { useSesion } from "../store/ContextoSesion";
import { claveDePunto, formatearDireccion, type RespuestaNominatim } from "../utilidades/direccion";

type Estado = { tipo: "sin_punto" } | { tipo: "cargando" } | { tipo: "ok"; texto: string } | { tipo: "sin_direccion" } | { tipo: "error" };

/** Lo ya consultado en esta sesión: reabrir un detalle o mover el marcador poco no vuelve a pedir la dirección. */
const CACHE = new Map<string, string | null>();
const ESPERA_MS = 600;

/**
 * Dirección aproximada de un punto. La consulta pasa por nuestro servidor (`/api/geocodificacion/inversa`),
 * que habla con OpenStreetMap: el navegador nunca se conecta a un tercero. Espera a que el punto deje de
 * cambiar (ESPERA_MS) para no consultar mientras se arrastra el marcador y recuerda las respuestas. Si el
 * servicio no responde, la pantalla sigue funcionando con las coordenadas.
 */
export function useDireccion(latitud: number | null, longitud: number | null): Estado {
  const { token } = useSesion();
  const [estado, setEstado] = useState<Estado>({ tipo: "sin_punto" });

  useEffect(() => {
    if (latitud === null || longitud === null) {
      setEstado({ tipo: "sin_punto" });
      return;
    }
    const clave = claveDePunto(latitud, longitud);
    if (CACHE.has(clave)) {
      const guardada = CACHE.get(clave) ?? null;
      setEstado(guardada ? { tipo: "ok", texto: guardada } : { tipo: "sin_direccion" });
      return;
    }
    if (!token) {
      setEstado({ tipo: "error" });
      return;
    }

    setEstado({ tipo: "cargando" });
    let vigente = true;
    const temporizador = window.setTimeout(() => {
      clienteApi
        .get<RespuestaNominatim>(`/api/geocodificacion/inversa?latitud=${latitud}&longitud=${longitud}`, token)
        .then((datos) => {
          const texto = formatearDireccion(datos);
          CACHE.set(clave, texto);
          if (vigente) setEstado(texto ? { tipo: "ok", texto } : { tipo: "sin_direccion" });
        })
        .catch(() => {
          if (vigente) setEstado({ tipo: "error" });
        });
    }, ESPERA_MS);

    // Si el punto cambia antes de responder, la respuesta vieja ya no se muestra (sí se recuerda).
    return () => {
      vigente = false;
      window.clearTimeout(temporizador);
    };
  }, [latitud, longitud, token]);

  return estado;
}

interface Props {
  latitud: number | null;
  longitud: number | null;
  /** Si viene, junto a la dirección aparece «Usar como referencia» para copiarla al campo de referencia. */
  alUsar?: (direccion: string) => void;
  etiqueta?: string;
}

/** Una línea con la dirección aproximada del punto, o el motivo por el que no se pudo obtener. */
export function DireccionPunto({ latitud, longitud, alUsar, etiqueta = "Dirección aproximada" }: Props) {
  const estado = useDireccion(latitud, longitud);

  return (
    <p className="text-sm text-texto" aria-live="polite">
      <span className="font-semibold">{etiqueta}: </span>
      {estado.tipo === "sin_punto" && <span className="text-textoSuave">marque un punto para ver su dirección.</span>}
      {estado.tipo === "cargando" && <span className="text-textoSuave">buscando…</span>}
      {estado.tipo === "sin_direccion" && <span className="text-textoSuave">no hay una dirección registrada para este punto.</span>}
      {estado.tipo === "error" && <span className="text-advertenciaTexto">no se pudo consultar. Las coordenadas siguen siendo válidas.</span>}
      {estado.tipo === "ok" && (
        <>
          {estado.texto}
          {alUsar && (
            <button
              type="button"
              onClick={() => alUsar(estado.texto)}
              className="ml-2 text-xs font-semibold text-primario hover:text-primarioOscuro"
            >
              Usar como referencia
            </button>
          )}
        </>
      )}
    </p>
  );
}
