import type { ReactNode } from "react";
import type { RespuestaAsistencia } from "../types/api";
import { formatearFechaHora } from "../utilidades/fechas";
import { PillEstadoValidacion } from "./PillEstadoValidacion";
import {
  describirRetraso,
  etiquetaTipo,
  explicacionEstado,
  formatearMetros,
  seSincronizoDespues,
  urlMapa,
} from "./reglas";

function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase text-textoSuave">{etiqueta}</dt>
      <dd className="mt-0.5 text-sm text-texto">{children}</dd>
    </div>
  );
}

/** Todo lo que se sabe de una marcación, para decidir si una observación o una sospecha es un problema real. */
export function DetalleMarcacion({ marcacion }: { marcacion: RespuestaAsistencia }) {
  const tieneCoordenadas = marcacion.latitud !== null && marcacion.longitud !== null;

  return (
    <div className="flex flex-col gap-5">
      <div className="rounded-sm border border-borde bg-fondo px-4 py-3">
        <PillEstadoValidacion estado={marcacion.estadoValidacion} />
        <p className="mt-2 text-sm text-texto">{explicacionEstado(marcacion.estadoValidacion)}</p>
      </div>

      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Dato etiqueta="Persona">{marcacion.nombreUsuario}</Dato>
        <Dato etiqueta="Evento">{etiquetaTipo(marcacion.tipoEvento)}</Dato>
        <Dato etiqueta="Ocurrió">{formatearFechaHora(marcacion.horaEvento)}</Dato>
        <Dato etiqueta="Llegó al servidor">
          {formatearFechaHora(marcacion.horaSincronizacion)}
          <span className={`ml-2 text-xs ${seSincronizoDespues(marcacion) ? "text-advertenciaTexto" : "text-textoSuave"}`}>
            {describirRetraso(marcacion.retrasoSincronizacionSegundos)}
          </span>
        </Dato>
        <Dato etiqueta="Sede">{marcacion.ubicacion ?? "Sin asignación vigente"}</Dato>
        <Dato etiqueta="Proyecto">{marcacion.proyecto ?? "—"}</Dato>
        <Dato etiqueta="Distancia a la sede">{formatearMetros(marcacion.distanciaMetros)}</Dato>
        <Dato etiqueta="Radio de tolerancia">{formatearMetros(marcacion.radioToleranciaMetros)}</Dato>
        <Dato etiqueta="Precisión del GPS">{formatearMetros(marcacion.precisionMetros)}</Dato>
        <Dato etiqueta="Dispositivo">{marcacion.dispositivo ?? "—"}</Dato>
        <Dato etiqueta="Coordenadas">
          {tieneCoordenadas ? (
            <>
              {marcacion.latitud!.toFixed(5)}, {marcacion.longitud!.toFixed(5)}{" "}
              <a
                href={urlMapa(marcacion.latitud!, marcacion.longitud!)}
                target="_blank"
                rel="noopener noreferrer"
                className="ml-1 font-semibold text-primario hover:text-primarioOscuro"
              >
                Ver en el mapa
              </a>
            </>
          ) : (
            "—"
          )}
        </Dato>
      </dl>

      {tieneCoordenadas && (
        <p className="text-xs text-textoSuave">
          «Ver en el mapa» abre un sitio externo (OpenStreetMap) con las coordenadas de esta marcación.
        </p>
      )}
    </div>
  );
}
