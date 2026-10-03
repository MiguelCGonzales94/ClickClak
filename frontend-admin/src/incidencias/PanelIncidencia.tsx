import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorHttp } from "../services/clienteApi";
import { servicioIncidencias } from "../services/servicioIncidencias";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaDetalleIncidencia, RespuestaIncidencia } from "../types/api";
import { formatearFecha, formatearFechaHora } from "../utilidades/fechas";
import { PillEstadoIncidencia } from "./PillEstadoIncidencia";
import {
  accionesDisponibles,
  ETIQUETA_ESTADO,
  ETIQUETA_TIPO,
  motivoDeBloqueo,
  requiereComentario,
  type AccionIncidencia,
} from "./reglas";

interface Props {
  incidenciaId: number;
  onCerrar: () => void;
  /** Se invoca con la incidencia ya actualizada para que el listado se refresque sin recargar todo. */
  onActualizada: (incidencia: RespuestaIncidencia) => void;
}

const ACCIONES: Record<AccionIncidencia, { boton: string; enCurso: string; estilo: string }> = {
  iniciarRevision: {
    boton: "Tomar para revisión",
    enCurso: "Tomando…",
    estilo: "bg-primario text-white hover:bg-primarioOscuro",
  },
  aprobar: { boton: "Aprobar", enCurso: "Aprobando…", estilo: "bg-exito text-white hover:bg-exitoTexto" },
  rechazar: { boton: "Rechazar", enCurso: "Rechazando…", estilo: "bg-peligro text-white hover:bg-peligroTexto" },
  cerrar: {
    boton: "Cerrar incidencia",
    enCurso: "Cerrando…",
    estilo: "bg-superficie text-texto border border-borde hover:bg-fondo",
  },
};

/** Panel lateral con el detalle de una incidencia, su historial y las acciones que el flujo permite. */
export function PanelIncidencia({ incidenciaId, onCerrar, onActualizada }: Props) {
  const { token, perfil } = useSesion();
  const [detalle, setDetalle] = useState<RespuestaDetalleIncidencia | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [comentario, setComentario] = useState("");
  const [enCurso, setEnCurso] = useState<AccionIncidencia | null>(null);
  const botonCerrar = useRef<HTMLButtonElement>(null);

  const cargar = useCallback(async () => {
    if (!token) return;
    try {
      setDetalle(await servicioIncidencias.obtener(token, incidenciaId));
      setError(null);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo cargar la incidencia");
    } finally {
      setCargando(false);
    }
  }, [token, incidenciaId]);

  useEffect(() => {
    setCargando(true);
    setDetalle(null);
    setComentario("");
    void cargar();
  }, [cargar]);

  // Accesibilidad del panel modal: el foco entra en él una sola vez y Esc lo cierra. Se guarda la
  // última función en una referencia: si el efecto dependiera de `onCerrar`, un padre que la recree
  // en cada render devolvería el foco al botón mientras la persona escribe un comentario.
  const alCerrar = useRef(onCerrar);
  alCerrar.current = onCerrar;
  useEffect(() => {
    botonCerrar.current?.focus();
    function alPulsarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") alCerrar.current();
    }
    document.addEventListener("keydown", alPulsarTecla);
    return () => document.removeEventListener("keydown", alPulsarTecla);
  }, []);

  async function ejecutar(accion: AccionIncidencia) {
    if (!token || !detalle) return;
    const texto = comentario.trim();
    if (requiereComentario(accion) && !texto) {
      setError("Indique el motivo del rechazo.");
      return;
    }
    setEnCurso(accion);
    setError(null);
    try {
      const id = detalle.incidencia.id;
      let actualizada: RespuestaIncidencia;
      switch (accion) {
        case "iniciarRevision":
          actualizada = await servicioIncidencias.iniciarRevision(token, id);
          break;
        case "aprobar":
          actualizada = await servicioIncidencias.aprobar(token, id, texto || undefined);
          break;
        case "rechazar":
          actualizada = await servicioIncidencias.rechazar(token, id, texto);
          break;
        case "cerrar":
          actualizada = await servicioIncidencias.cerrar(token, id, texto || undefined);
          break;
      }
      onActualizada(actualizada);
      setComentario("");
      await cargar();
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo completar la acción");
      // Un 409 significa que otra persona ya movió la incidencia: se recarga para mostrar el estado real.
      if (err instanceof ErrorHttp && err.status === 409) await cargar();
    } finally {
      setEnCurso(null);
    }
  }

  const incidencia = detalle?.incidencia;
  const acciones = incidencia ? accionesDisponibles(incidencia.estado) : [];
  const bloqueo =
    incidencia && perfil
      ? acciones.map((accion) => motivoDeBloqueo(accion, incidencia, perfil.id)).find((motivo) => motivo !== null) ?? null
      : null;
  const pideComentario = acciones.some((accion) => accion === "aprobar" || accion === "rechazar" || accion === "cerrar");

  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      <div className="absolute inset-0 bg-[#061229]/45" onClick={onCerrar} aria-hidden="true" />

      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-panel-incidencia"
        className="relative w-full max-w-lg h-full bg-superficie shadow-[-12px_0_36px_rgba(10,16,32,0.2)] flex flex-col"
      >
        <header className="h-16 px-6 border-b border-borde flex items-center gap-3 shrink-0">
          <h2 id="titulo-panel-incidencia" className="text-base font-bold text-texto flex-1 truncate">
            {incidencia ? `Incidencia #${incidencia.id} · ${ETIQUETA_TIPO[incidencia.tipo]}` : "Incidencia"}
          </h2>
          {incidencia && <PillEstadoIncidencia estado={incidencia.estado} />}
          <button
            ref={botonCerrar}
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar el detalle"
            className="h-9 w-9 rounded-sm text-textoSuave hover:bg-fondo hover:text-texto transition-colors text-xl leading-none"
          >
            ×
          </button>
        </header>

        <div className="flex-1 overflow-y-auto px-6 py-5 flex flex-col gap-6">
          {cargando && <p className="text-sm text-textoSuave">Cargando…</p>}

          {!cargando && !incidencia && error && (
            <p role="alert" className="text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
              {error}
            </p>
          )}

          {incidencia && detalle && (
            <>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-4 text-sm">
                <Dato etiqueta="Colaborador" valor={incidencia.nombreUsuario} />
                <Dato etiqueta="Fecha del evento" valor={formatearFecha(incidencia.fechaEvento)} />
                <Dato
                  etiqueta="Registrada por"
                  valor={`${incidencia.nombreCreadoPor}${incidencia.creadoPorId === incidencia.usuarioId ? " (el propio colaborador)" : ""}`}
                />
                <Dato etiqueta="Registrada el" valor={formatearFechaHora(incidencia.creadoEn)} />
                {incidencia.marcacionId !== null && <Dato etiqueta="Marcación asociada" valor={`#${incidencia.marcacionId}`} />}
                {incidencia.nombreRevisadoPor && (
                  <Dato
                    etiqueta="Resuelta por"
                    valor={`${incidencia.nombreRevisadoPor}${incidencia.revisadoEn ? ` · ${formatearFechaHora(incidencia.revisadoEn)}` : ""}`}
                  />
                )}
              </dl>

              <section aria-labelledby="titulo-descripcion">
                <h3 id="titulo-descripcion" className="text-xs font-semibold uppercase tracking-wide text-textoSuave mb-2">
                  Descripción
                </h3>
                <p className="text-sm text-texto whitespace-pre-wrap bg-fondo rounded-sm px-3 py-2">{incidencia.descripcion}</p>
              </section>

              {incidencia.comentarioRevision && (
                <section aria-labelledby="titulo-comentario">
                  <h3 id="titulo-comentario" className="text-xs font-semibold uppercase tracking-wide text-textoSuave mb-2">
                    Comentario de la revisión
                  </h3>
                  <p className="text-sm text-texto whitespace-pre-wrap bg-fondo rounded-sm px-3 py-2">{incidencia.comentarioRevision}</p>
                </section>
              )}

              <section aria-labelledby="titulo-historial">
                <h3 id="titulo-historial" className="text-xs font-semibold uppercase tracking-wide text-textoSuave mb-3">
                  Historial
                </h3>
                <ol className="flex flex-col gap-4 border-l-2 border-borde ml-1.5">
                  {detalle.historial.map((registro, indice) => (
                    <li key={indice} className="relative pl-5">
                      <span className="absolute -left-[7px] top-1 h-3 w-3 rounded-full bg-primario border-2 border-superficie" aria-hidden="true" />
                      <p className="text-sm font-semibold text-texto">
                        {registro.estadoAnterior === null
                          ? "Registrada"
                          : `${ETIQUETA_ESTADO[registro.estadoAnterior]} → ${ETIQUETA_ESTADO[registro.estadoNuevo]}`}
                      </p>
                      <p className="text-xs text-textoSuave">
                        {registro.nombreUsuario} · {formatearFechaHora(registro.creadoEn)}
                      </p>
                      {registro.comentario && <p className="mt-1 text-sm text-texto whitespace-pre-wrap">{registro.comentario}</p>}
                    </li>
                  ))}
                </ol>
              </section>
            </>
          )}
        </div>

        {incidencia && acciones.length > 0 && (
          <footer className="border-t border-borde px-6 py-4 flex flex-col gap-3 shrink-0 bg-superficie">
            {bloqueo && (
              <p role="note" className="text-sm text-advertenciaTexto bg-advertenciaFondo border border-advertencia/30 rounded-sm px-3 py-2">
                {bloqueo}
              </p>
            )}

            {pideComentario && (
              <label className="flex flex-col gap-1 text-sm text-texto">
                <span className="font-semibold">
                  Comentario {acciones.includes("rechazar") ? "(obligatorio para rechazar)" : "(opcional)"}
                </span>
                <textarea
                  value={comentario}
                  onChange={(evento) => setComentario(evento.target.value)}
                  maxLength={1000}
                  rows={3}
                  className="px-3 py-2 rounded-sm border border-borde text-sm text-texto bg-superficie focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario resize-none"
                />
              </label>
            )}

            {error && (
              <p role="alert" className="text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
                {error}
              </p>
            )}

            <div className="flex flex-wrap gap-2">
              {acciones.map((accion) => {
                const bloqueada = perfil ? motivoDeBloqueo(accion, incidencia, perfil.id) !== null : true;
                return (
                  <button
                    key={accion}
                    type="button"
                    onClick={() => void ejecutar(accion)}
                    disabled={bloqueada || enCurso !== null}
                    className={`h-10 px-4 rounded-sm text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${ACCIONES[accion].estilo}`}
                  >
                    {enCurso === accion ? ACCIONES[accion].enCurso : ACCIONES[accion].boton}
                  </button>
                );
              })}
            </div>
          </footer>
        )}
      </aside>
    </div>
  );
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wide text-textoSuave">{etiqueta}</dt>
      <dd className="mt-0.5 text-texto">{valor}</dd>
    </div>
  );
}
