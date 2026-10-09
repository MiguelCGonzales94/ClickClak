import { useEffect, useMemo, useRef, useState } from "react";
import { DetalleMarcacion } from "../asistencia/DetalleMarcacion";
import { PillEstadoValidacion } from "../asistencia/PillEstadoValidacion";
import {
  describirRetraso,
  ESTADOS_VALIDACION,
  etiquetaTipo,
  filtrosIniciales,
  formatearMetros,
  rangoRapido,
  type RangoRapido,
  seSincronizoDespues,
  TIPOS_EVENTO,
  totalPorRevisar,
  validarRango,
} from "../asistencia/reglas";
import { Modal } from "../components/Modal";
import { Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAsistencia } from "../services/servicioAsistencia";
import { servicioUbicaciones } from "../services/servicioUbicaciones";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { useSesion } from "../store/ContextoSesion";
import type {
  EstadoValidacion,
  FiltrosAsistencia,
  RespuestaAsistencia,
  RespuestaPagina,
  RespuestaResumenAsistencia,
  RespuestaUbicacion,
  RespuestaUsuario,
  TipoEvento,
} from "../types/api";
import { formatearFechaHora, hoyEnLima } from "../utilidades/fechas";

/** Supervisión de asistencia: marcaciones del personal con su estado de validación, por día, persona y sede. */
export function PaginaAsistencia() {
  const { token } = useSesion();
  const hoy = useMemo(() => hoyEnLima(), []);
  const [filtros, setFiltros] = useState<FiltrosAsistencia>(() => filtrosIniciales(hoy));
  const [personas, setPersonas] = useState<RespuestaUsuario[]>([]);
  const [sedes, setSedes] = useState<RespuestaUbicacion[]>([]);
  const [resultado, setResultado] = useState<RespuestaPagina<RespuestaAsistencia> | null>(null);
  const [resumen, setResumen] = useState<RespuestaResumenAsistencia | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [detalle, setDetalle] = useState<RespuestaAsistencia | null>(null);
  const peticion = useRef(0);

  const errorRango = validarRango(filtros.desde, filtros.hasta);

  // Catálogos de los filtros: se piden una sola vez.
  useEffect(() => {
    if (!token) return;
    // Todos los usuarios, no solo colaboradores: cualquier usuario autenticado puede marcar su propia asistencia.
    servicioUsuarios.listar(token).then(setPersonas).catch(() => setPersonas([]));
    servicioUbicaciones.listar(token).then(setSedes).catch(() => setSedes([]));
  }, [token]);

  useEffect(() => {
    if (!token || errorRango) return;
    const numero = ++peticion.current;
    setCargando(true);
    setError(null);
    Promise.all([
      servicioAsistencia.listar(token, filtros),
      // El resumen reparte entre todos los estados, así que no lleva el filtro de estado ni la página.
      servicioAsistencia.resumen(token, filtros),
    ])
      .then(([pagina, reparto]) => {
        if (numero !== peticion.current) return;
        setResultado(pagina);
        setResumen(reparto);
      })
      .catch((err) => {
        if (numero === peticion.current) {
          setError(err instanceof ErrorHttp ? err.message : "No se pudo cargar la asistencia");
        }
      })
      .finally(() => {
        if (numero === peticion.current) setCargando(false);
      });
  }, [token, filtros, errorRango, version]);

  function cambiar(parcial: Partial<FiltrosAsistencia>) {
    setFiltros((anteriores) => ({ ...anteriores, ...parcial, pagina: 0 }));
  }

  function aplicarRango(rango: RangoRapido) {
    cambiar(rangoRapido(rango, hoy));
  }

  const desde = resultado && resultado.total > 0 ? resultado.pagina * resultado.tamano + 1 : 0;
  const hasta = resultado ? Math.min((resultado.pagina + 1) * resultado.tamano, resultado.total) : 0;
  const hayFiltros =
    filtros.usuarioId !== "" || filtros.ubicacionId !== "" || filtros.estado !== "" || filtros.tipoEvento !== "";

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center justify-between shrink-0">
        <h1 className="text-lg font-bold text-texto">Asistencia</h1>
        <button
          onClick={() => setVersion((valor) => valor + 1)}
          className="h-9 px-4 rounded-sm border border-borde text-sm font-semibold text-texto hover:bg-fondo transition-colors"
        >
          Actualizar
        </button>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-4">
        <section className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Desde</span>
            <input
              type="date"
              value={filtros.desde}
              max={filtros.hasta || undefined}
              onChange={(e) => cambiar({ desde: e.target.value })}
              className="campo"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Hasta</span>
            <input
              type="date"
              value={filtros.hasta}
              min={filtros.desde || undefined}
              onChange={(e) => cambiar({ hasta: e.target.value })}
              className="campo"
            />
          </label>
          <div className="flex h-11 items-center gap-1" role="group" aria-label="Atajos de fecha">
            {(
              [
                ["hoy", "Hoy"],
                ["ayer", "Ayer"],
                ["7dias", "Últimos 7 días"],
              ] as [RangoRapido, string][]
            ).map(([rango, etiqueta]) => (
              <button
                key={rango}
                onClick={() => aplicarRango(rango)}
                className="h-9 rounded-sm border border-borde px-3 text-xs font-semibold text-texto hover:bg-fondo"
              >
                {etiqueta}
              </button>
            ))}
          </div>

          <label className="flex min-w-[180px] flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Persona</span>
            <select value={filtros.usuarioId} onChange={(e) => cambiar({ usuarioId: e.target.value })} className="campo">
              <option value="">Todas</option>
              {personas.map((persona) => (
                <option key={persona.id} value={persona.id}>
                  {persona.nombres} {persona.apellidos}
                  {persona.rol !== "COLABORADOR" ? ` · ${persona.rol}` : ""}
                </option>
              ))}
            </select>
          </label>
          <label className="flex min-w-[160px] flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Sede</span>
            <select value={filtros.ubicacionId} onChange={(e) => cambiar({ ubicacionId: e.target.value })} className="campo">
              <option value="">Todas</option>
              {sedes.map((sede) => (
                <option key={sede.id} value={sede.id}>
                  {sede.nombre}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Evento</span>
            <select
              value={filtros.tipoEvento}
              onChange={(e) => cambiar({ tipoEvento: e.target.value as TipoEvento | "" })}
              className="campo"
            >
              <option value="">Todos</option>
              {TIPOS_EVENTO.map((tipo) => (
                <option key={tipo.valor} value={tipo.valor}>
                  {tipo.etiqueta}
                </option>
              ))}
            </select>
          </label>
          {hayFiltros && (
            <button
              onClick={() => cambiar({ usuarioId: "", ubicacionId: "", estado: "", tipoEvento: "" })}
              className="h-11 px-3 text-sm font-semibold text-primario hover:text-primarioOscuro"
            >
              Limpiar filtros
            </button>
          )}
        </section>

        {errorRango && (
          <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
            {errorRango}
          </p>
        )}
        {error && (
          <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
            {error}
          </p>
        )}

        <section aria-label="Resumen por estado" className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => cambiar({ estado: "" })}
            aria-pressed={filtros.estado === ""}
            className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${
              filtros.estado === "" ? "border-primario bg-primario text-white" : "border-borde text-texto hover:bg-fondo"
            }`}
          >
            Todas · {resumen?.total ?? "—"}
          </button>
          {ESTADOS_VALIDACION.map((estado) => (
            <button
              key={estado.valor}
              onClick={() => cambiar({ estado: filtros.estado === estado.valor ? "" : (estado.valor as EstadoValidacion) })}
              aria-pressed={filtros.estado === estado.valor}
              title={estado.explicacion}
              className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${
                filtros.estado === estado.valor ? "border-primario bg-primario text-white" : "border-borde text-texto hover:bg-fondo"
              }`}
            >
              {estado.etiqueta} · {resumen?.porEstado[estado.valor] ?? "—"}
            </button>
          ))}
          {resumen && totalPorRevisar(resumen.porEstado) > 0 && (
            <span className="ml-1 text-xs font-semibold text-advertenciaTexto">
              {totalPorRevisar(resumen.porEstado)} por revisar
            </span>
          )}
        </section>

        <Tabla
          cargando={cargando && !resultado}
          vacio={
            errorRango
              ? "Corrija el rango de fechas para ver la asistencia."
              : "No hay marcaciones para los filtros elegidos."
          }
          columnas={["Persona", "Evento", "Hora", "Sede", "Estado", "Distancia", "Sincronización", ""]}
          filas={(errorRango ? [] : (resultado?.contenido ?? [])).map((marcacion) => [
            <span key="persona" className="font-medium">
              {marcacion.nombreUsuario}
            </span>,
            <span key="evento" className="text-textoSuave">
              {etiquetaTipo(marcacion.tipoEvento)}
            </span>,
            <span key="hora" className="whitespace-nowrap text-textoSuave">
              {formatearFechaHora(marcacion.horaEvento)}
            </span>,
            <div key="sede">
              <p>{marcacion.ubicacion ?? <span className="text-textoSuave">Sin asignación</span>}</p>
              {marcacion.proyecto && <p className="text-xs text-textoSuave">{marcacion.proyecto}</p>}
            </div>,
            <PillEstadoValidacion key="estado" estado={marcacion.estadoValidacion} />,
            <span key="distancia" className="whitespace-nowrap text-textoSuave">
              {formatearMetros(marcacion.distanciaMetros)}
            </span>,
            <span
              key="sync"
              className={`whitespace-nowrap text-xs ${seSincronizoDespues(marcacion) ? "font-semibold text-advertenciaTexto" : "text-textoSuave"}`}
            >
              {describirRetraso(marcacion.retrasoSincronizacionSegundos)}
            </span>,
            <button
              key="detalle"
              onClick={() => setDetalle(marcacion)}
              className="text-xs font-semibold text-primario hover:text-primarioOscuro"
            >
              Ver detalle
            </button>,
          ])}
        />

        {resultado && !errorRango && (
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-textoSuave">
            <span>
              {resultado.total === 0 ? "0 marcaciones" : `Mostrando ${desde}–${hasta} de ${resultado.total} marcaciones`}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={resultado.pagina === 0 || cargando}
                onClick={() => setFiltros({ ...filtros, pagina: filtros.pagina - 1 })}
                className="h-9 rounded-sm border border-borde px-3 font-semibold text-texto hover:bg-fondo disabled:opacity-40"
              >
                Anterior
              </button>
              <span>
                Página {resultado.totalPaginas === 0 ? 0 : resultado.pagina + 1} de {resultado.totalPaginas}
              </span>
              <button
                disabled={resultado.pagina + 1 >= resultado.totalPaginas || cargando}
                onClick={() => setFiltros({ ...filtros, pagina: filtros.pagina + 1 })}
                className="h-9 rounded-sm border border-borde px-3 font-semibold text-texto hover:bg-fondo disabled:opacity-40"
              >
                Siguiente
              </button>
            </div>
          </div>
        )}
      </main>

      {detalle && (
        <Modal titulo={`Marcación de ${detalle.nombreUsuario}`} ancho="md" onCerrar={() => setDetalle(null)}>
          <DetalleMarcacion marcacion={detalle} />
        </Modal>
      )}
    </>
  );
}
