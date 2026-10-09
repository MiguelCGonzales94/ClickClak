import { useCallback, useEffect, useMemo, useState } from "react";
import { FormularioAsignacion } from "../asignaciones/FormularioAsignacion";
import { FormularioMoverAsignacion } from "../asignaciones/FormularioMoverAsignacion";
import {
  accionesDisponibles,
  clasesEstado,
  ESTADOS_ASIGNACION,
  etiquetaEstado,
  filtrarAsignaciones,
  type FiltroEstado,
  sedesVigentesPorTecnico,
} from "../asignaciones/reglas";
import { Modal } from "../components/Modal";
import { Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAsignaciones } from "../services/servicioAsignaciones";
import { servicioHorarios } from "../services/servicioHorarios";
import { servicioProyectos } from "../services/servicioProyectos";
import { servicioUbicaciones } from "../services/servicioUbicaciones";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { useSesion } from "../store/ContextoSesion";
import type {
  RespuestaAsignacion,
  RespuestaHorario,
  RespuestaProyecto,
  RespuestaUbicacion,
  RespuestaUsuario,
} from "../types/api";
import { hoyEnLima } from "../utilidades/fechas";
import { MenuAcciones, type OpcionMenu } from "../usuarios/MenuAcciones";

type Dialogo =
  | { tipo: "crear" }
  | { tipo: "editar"; asignacion: RespuestaAsignacion }
  | { tipo: "mover"; asignacion: RespuestaAsignacion }
  | { tipo: "quitar"; asignacion: RespuestaAsignacion };

/**
 * HU08: asignación de técnicos a un servicio, sede y turno, en un rango de fechas. Un técnico puede
 * tener varias sedes a la vez; las asignaciones se editan, se quitan o se pasan a otra sede.
 */
export function PaginaAsignaciones() {
  const { token } = useSesion();
  const hoy = useMemo(() => hoyEnLima(), []);
  const [asignaciones, setAsignaciones] = useState<RespuestaAsignacion[]>([]);
  const [tecnicos, setTecnicos] = useState<RespuestaUsuario[]>([]);
  const [proyectos, setProyectos] = useState<RespuestaProyecto[]>([]);
  const [ubicaciones, setUbicaciones] = useState<RespuestaUbicacion[]>([]);
  const [horarios, setHorarios] = useState<RespuestaHorario[]>([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);

  const [filtroTecnico, setFiltroTecnico] = useState("");
  const [filtroEstado, setFiltroEstado] = useState<FiltroEstado>("ACTIVAS");
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [ejecutando, setEjecutando] = useState(false);

  const cargarTodo = useCallback(() => {
    if (!token) return;
    setCargando(true);
    setErrorCarga(null);
    Promise.all([
      servicioAsignaciones.listar(token),
      servicioUsuarios.listar(token, "COLABORADOR", true),
      servicioProyectos.listar(token),
      servicioUbicaciones.listar(token),
      servicioHorarios.listar(token),
    ])
      .then(([a, t, p, u, h]) => {
        setAsignaciones(a);
        setTecnicos(t);
        setProyectos(p);
        setUbicaciones(u);
        setHorarios(h);
      })
      .catch((err) => setErrorCarga(err instanceof ErrorHttp ? err.message : "No se pudieron cargar las asignaciones"))
      .finally(() => setCargando(false));
  }, [token]);

  useEffect(cargarTodo, [cargarTodo]);

  const sedesVigentes = useMemo(() => sedesVigentesPorTecnico(asignaciones), [asignaciones]);
  const visibles = useMemo(
    () => filtrarAsignaciones(asignaciones, { usuarioId: filtroTecnico, estado: filtroEstado }),
    [asignaciones, filtroTecnico, filtroEstado],
  );

  function cerrarDialogo() {
    setDialogo(null);
    setErrorAccion(null);
  }

  function terminar(mensaje: string) {
    cerrarDialogo();
    setAviso(mensaje);
    cargarTodo();
  }

  async function confirmarQuitar(asignacion: RespuestaAsignacion) {
    if (!token) return;
    setEjecutando(true);
    setErrorAccion(null);
    try {
      await servicioAsignaciones.quitar(token, asignacion.id);
      terminar(`Se quitó a ${asignacion.nombreUsuario} de ${asignacion.nombreUbicacion}.`);
    } catch (err) {
      setErrorAccion(err instanceof ErrorHttp ? err.message : "No se pudo quitar la asignación");
    } finally {
      setEjecutando(false);
    }
  }

  function opcionesDe(asignacion: RespuestaAsignacion): OpcionMenu[] {
    const permitidas = accionesDisponibles(asignacion);
    const opciones: OpcionMenu[] = [];
    if (permitidas.editar) opciones.push({ etiqueta: "Editar", alElegir: () => setDialogo({ tipo: "editar", asignacion }) });
    if (permitidas.mover) opciones.push({ etiqueta: "Mover a otra sede", alElegir: () => setDialogo({ tipo: "mover", asignacion }) });
    if (permitidas.quitar) opciones.push({ etiqueta: "Quitar", peligro: true, alElegir: () => setDialogo({ tipo: "quitar", asignacion }) });
    return opciones;
  }

  const hayFiltros = filtroTecnico !== "" || filtroEstado !== "ACTIVAS";

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center justify-between shrink-0">
        <h1 className="text-lg font-bold text-texto">Asignaciones</h1>
        <button
          onClick={() => setDialogo({ tipo: "crear" })}
          className="h-9 px-4 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro transition-colors"
        >
          + Nueva asignación
        </button>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-4">
        {aviso && (
          <p
            role="status"
            className="flex items-center justify-between gap-3 rounded-sm border border-exito/30 bg-exitoFondo px-3 py-2 text-sm text-exitoTexto"
          >
            {aviso}
            <button onClick={() => setAviso(null)} aria-label="Cerrar aviso" className="text-lg leading-none">
              ×
            </button>
          </p>
        )}
        {errorCarga && (
          <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
            {errorCarga}
          </p>
        )}

        <div className="flex flex-wrap items-end gap-3">
          <label className="flex min-w-[220px] flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Técnico</span>
            <select value={filtroTecnico} onChange={(e) => setFiltroTecnico(e.target.value)} className="campo">
              <option value="">Todos</option>
              {tecnicos.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nombres} {t.apellidos}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Mostrar</span>
            <select value={filtroEstado} onChange={(e) => setFiltroEstado(e.target.value as FiltroEstado)} className="campo">
              <option value="ACTIVAS">Vigentes y programadas</option>
              {ESTADOS_ASIGNACION.map((estado) => (
                <option key={estado.valor} value={estado.valor}>
                  Solo {estado.etiqueta.toLowerCase()}s
                </option>
              ))}
              <option value="TODAS">Todas</option>
            </select>
          </label>
          {hayFiltros && (
            <button
              onClick={() => {
                setFiltroTecnico("");
                setFiltroEstado("ACTIVAS");
              }}
              className="h-11 px-3 text-sm font-semibold text-primario hover:text-primarioOscuro"
            >
              Limpiar filtros
            </button>
          )}
        </div>

        <Tabla
          cargando={cargando}
          vacio={hayFiltros ? "Ninguna asignación coincide con los filtros." : "No hay asignaciones registradas todavía."}
          columnas={["Técnico", "Servicio", "Sede", "Turno", "Desde", "Hasta", "Estado", ""]}
          filas={visibles.map((a) => [
            <div key="tecnico">
              <p className="font-medium">{a.nombreUsuario}</p>
              {(sedesVigentes[a.usuarioId] ?? 0) > 1 && (
                <p className="text-xs text-textoSuave">{sedesVigentes[a.usuarioId]} sedes vigentes</p>
              )}
            </div>,
            a.nombreProyecto,
            a.nombreUbicacion,
            a.nombreHorario,
            a.fechaInicio,
            a.fechaFin ?? "—",
            <span key="estado" className={`text-xs font-semibold px-2 py-1 rounded-full whitespace-nowrap ${clasesEstado(a.estado)}`}>
              {etiquetaEstado(a.estado)}
            </span>,
            <div key="acciones" className="text-right">
              <MenuAcciones etiqueta={`Acciones de ${a.nombreUsuario} en ${a.nombreUbicacion}`} opciones={opcionesDe(a)} />
            </div>,
          ])}
        />
      </main>

      {dialogo?.tipo === "crear" && token && (
        <Modal titulo="Nueva asignación" ancho="md" onCerrar={cerrarDialogo}>
          <FormularioAsignacion
            token={token}
            tecnicos={tecnicos}
            proyectos={proyectos}
            ubicaciones={ubicaciones}
            horarios={horarios}
            tecnicoInicialId={filtroTecnico}
            alCancelar={cerrarDialogo}
            alGuardar={(creada) => terminar(`Se asignó a ${creada.nombreUsuario} a ${creada.nombreUbicacion}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "editar" && token && (
        <Modal titulo="Editar asignación" ancho="md" onCerrar={cerrarDialogo}>
          <FormularioAsignacion
            token={token}
            asignacion={dialogo.asignacion}
            tecnicos={tecnicos}
            proyectos={proyectos}
            ubicaciones={ubicaciones}
            horarios={horarios}
            alCancelar={cerrarDialogo}
            alGuardar={(guardada) => terminar(`Se guardaron los cambios de ${guardada.nombreUsuario} en ${guardada.nombreUbicacion}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "mover" && token && (
        <Modal titulo="Mover a otra sede" ancho="md" onCerrar={cerrarDialogo}>
          <FormularioMoverAsignacion
            token={token}
            asignacion={dialogo.asignacion}
            proyectos={proyectos}
            ubicaciones={ubicaciones}
            horarios={horarios}
            hoy={hoy}
            alCancelar={cerrarDialogo}
            alGuardar={(nueva) => terminar(`Se movió a ${nueva.nombreUsuario} a ${nueva.nombreUbicacion} desde el ${nueva.fechaInicio}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "quitar" && (
        <Modal titulo="Quitar asignación" onCerrar={cerrarDialogo}>
          <p className="text-sm text-texto">
            <strong>{dialogo.asignacion.nombreUsuario}</strong> dejará de estar asignado a{" "}
            <strong>{dialogo.asignacion.nombreUbicacion}</strong> ({dialogo.asignacion.nombreProyecto}). Las
            marcaciones que ya hizo ahí se conservan. Si lo que quiere es pasarlo a otra sede, use «Mover a otra sede».
          </p>
          {errorAccion && (
            <p role="alert" className="mt-3 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
              {errorAccion}
            </p>
          )}
          <div className="mt-5 flex justify-end gap-3">
            <button onClick={cerrarDialogo} className="h-10 rounded-sm border border-borde px-4 text-sm font-semibold text-texto hover:bg-fondo">
              Cancelar
            </button>
            <button
              disabled={ejecutando}
              onClick={() => confirmarQuitar(dialogo.asignacion)}
              className="h-10 rounded-sm bg-peligro px-4 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-60"
            >
              {ejecutando ? "Quitando…" : "Quitar"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
