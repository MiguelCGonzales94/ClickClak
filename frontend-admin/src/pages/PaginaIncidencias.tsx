import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Tabla } from "../components/Tabla";
import { FormularioIncidencia } from "../incidencias/FormularioIncidencia";
import { PanelIncidencia } from "../incidencias/PanelIncidencia";
import { PillEstadoIncidencia } from "../incidencias/PillEstadoIncidencia";
import {
  contarPorEstado,
  ESTADOS,
  ETIQUETA_ESTADO,
  ETIQUETA_TIPO,
  esPendienteDeRevision,
  filtrarIncidencias,
} from "../incidencias/reglas";
import { ErrorHttp } from "../services/clienteApi";
import { servicioIncidencias } from "../services/servicioIncidencias";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { useSesion } from "../store/ContextoSesion";
import type { EstadoIncidencia, RespuestaIncidencia, RespuestaUsuario } from "../types/api";
import { formatearFecha } from "../utilidades/fechas";

/**
 * Bandeja de incidencias laborales para supervisores y RRHH: ver lo pendiente, revisarlo y
 * registrar incidencias a nombre de un colaborador. El filtro de estado vive en la URL
 * (?estado=EN_REVISION) para poder enlazarlo desde el dashboard o compartirlo.
 */
export function PaginaIncidencias() {
  const { token } = useSesion();
  const [parametros, setParametros] = useSearchParams();
  const [incidencias, setIncidencias] = useState<RespuestaIncidencia[]>([]);
  const [usuarios, setUsuarios] = useState<RespuestaUsuario[]>([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [busqueda, setBusqueda] = useState("");
  const [seleccionadaId, setSeleccionadaId] = useState<number | null>(null);
  const [formularioAbierto, setFormularioAbierto] = useState(false);

  const estadoParametro = parametros.get("estado");
  const estadoFiltro: EstadoIncidencia | null = ESTADOS.includes(estadoParametro as EstadoIncidencia)
    ? (estadoParametro as EstadoIncidencia)
    : null;

  const cargar = useCallback(async () => {
    if (!token) return;
    setCargando(true);
    try {
      const [lista, activos] = await Promise.all([
        servicioIncidencias.listar(token),
        servicioUsuarios.listar(token, undefined, true),
      ]);
      setIncidencias(lista);
      setUsuarios(activos);
      setErrorCarga(null);
    } catch (err) {
      setErrorCarga(err instanceof ErrorHttp ? err.message : "No se pudieron cargar las incidencias");
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  const conteo = useMemo(() => contarPorEstado(incidencias), [incidencias]);
  const visibles = useMemo(
    () => filtrarIncidencias(incidencias, { estado: estadoFiltro, texto: busqueda }),
    [incidencias, estadoFiltro, busqueda],
  );
  const porRevisar = conteo.REGISTRADA + conteo.EN_REVISION;

  function elegirEstado(estado: EstadoIncidencia | null) {
    setParametros(estado ? { estado } : {}, { replace: true });
  }

  /** Refresca la fila de una incidencia sin volver a pedir toda la lista. */
  function reemplazar(actualizada: RespuestaIncidencia) {
    setIncidencias((actuales) => actuales.map((i) => (i.id === actualizada.id ? actualizada : i)));
  }

  const cerrarPanel = useCallback(() => setSeleccionadaId(null), []);
  const cerrarFormulario = useCallback(() => setFormularioAbierto(false), []);

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <h1 className="text-lg font-bold text-texto">Incidencias</h1>
          {!cargando && porRevisar > 0 && (
            <span className="text-xs font-semibold px-2 py-1 rounded-full bg-advertenciaFondo text-advertenciaTexto">
              {porRevisar} por revisar
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={() => setFormularioAbierto(true)}
          className="h-10 px-4 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro transition-colors"
        >
          + Registrar incidencia
        </button>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-5">
        <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Filtrar por estado">
          <FiltroEstado activo={estadoFiltro === null} onClick={() => elegirEstado(null)} etiqueta="Todas" cantidad={incidencias.length} />
          {ESTADOS.map((estado) => (
            <FiltroEstado
              key={estado}
              activo={estadoFiltro === estado}
              onClick={() => elegirEstado(estado)}
              etiqueta={ETIQUETA_ESTADO[estado]}
              cantidad={conteo[estado]}
              pendiente={esPendienteDeRevision(estado)}
            />
          ))}
        </div>

        <label className="h-11 max-w-md rounded-sm border border-borde bg-superficie px-3 flex items-center gap-2 text-sm focus-within:ring-2 focus-within:ring-primario/25 focus-within:border-primario">
          <span className="sr-only">Buscar incidencias</span>
          <input
            type="search"
            value={busqueda}
            onChange={(evento) => setBusqueda(evento.target.value)}
            placeholder="Buscar por colaborador, tipo o descripción…"
            className="min-w-0 flex-1 bg-transparent outline-none text-texto placeholder:text-textoSuave"
          />
        </label>

        {errorCarga && (
          <div role="alert" className="flex items-center justify-between gap-4 text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-4 py-3">
            <span>{errorCarga}</span>
            <button type="button" onClick={() => void cargar()} className="font-semibold underline">
              Reintentar
            </button>
          </div>
        )}

        <Tabla
          cargando={cargando}
          vacio={
            incidencias.length === 0
              ? "Todavía no hay incidencias registradas."
              : "Ninguna incidencia coincide con el filtro."
          }
          columnas={["Fecha", "Colaborador", "Tipo", "Estado", "Registrada por", "Resuelta por", ""]}
          filas={visibles.map((incidencia) => [
            formatearFecha(incidencia.fechaEvento),
            incidencia.nombreUsuario,
            ETIQUETA_TIPO[incidencia.tipo],
            <PillEstadoIncidencia key="estado" estado={incidencia.estado} />,
            incidencia.nombreCreadoPor,
            incidencia.nombreRevisadoPor ?? "—",
            <button
              key="abrir"
              type="button"
              onClick={() => setSeleccionadaId(incidencia.id)}
              aria-label={`Abrir la incidencia ${incidencia.id} de ${incidencia.nombreUsuario}`}
              className="h-8 px-3 rounded-sm border border-borde text-xs font-semibold text-primario hover:bg-primario/5 transition-colors"
            >
              {esPendienteDeRevision(incidencia.estado) ? "Revisar" : "Ver"}
            </button>,
          ])}
        />
      </main>

      {seleccionadaId !== null && (
        <PanelIncidencia incidenciaId={seleccionadaId} onCerrar={cerrarPanel} onActualizada={reemplazar} />
      )}

      {formularioAbierto && (
        <FormularioIncidencia
          usuarios={usuarios}
          onCerrar={cerrarFormulario}
          onCreada={(creada) => {
            setFormularioAbierto(false);
            setSeleccionadaId(creada.id);
            void cargar();
          }}
        />
      )}
    </>
  );
}

function FiltroEstado({
  activo,
  onClick,
  etiqueta,
  cantidad,
  pendiente = false,
}: {
  activo: boolean;
  onClick: () => void;
  etiqueta: string;
  cantidad: number;
  pendiente?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={activo}
      className={`h-9 px-3 rounded-full text-sm font-semibold border transition-colors flex items-center gap-2 ${
        activo
          ? "bg-primario text-white border-primario"
          : "bg-superficie text-texto border-borde hover:bg-fondo"
      }`}
    >
      {etiqueta}
      <span
        className={`text-xs px-1.5 py-0.5 rounded-full ${
          activo
            ? "bg-white/20 text-white"
            : pendiente && cantidad > 0
              ? "bg-advertenciaFondo text-advertenciaTexto"
              : "bg-fondo text-textoSuave"
        }`}
      >
        {cantidad}
      </span>
    </button>
  );
}
