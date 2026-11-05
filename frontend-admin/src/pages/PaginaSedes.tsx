import { useEffect, useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { Modal } from "../components/Modal";
import { EstadoPill, Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioProyectos } from "../services/servicioProyectos";
import { servicioUbicaciones } from "../services/servicioUbicaciones";
import { useSesion } from "../store/ContextoSesion";
import { FormularioSede } from "../sedes/FormularioSede";
import { formatearCoordenadas } from "../sedes/reglas";
import type { RespuestaProyecto, RespuestaUbicacion } from "../types/api";
import { MenuAcciones, type OpcionMenu } from "../usuarios/MenuAcciones";

const PROYECTO_VACIO = { nombre: "", cliente: "", fechaInicio: "" };

type Dialogo =
  | { tipo: "crear" }
  | { tipo: "editar"; sede: RespuestaUbicacion }
  | { tipo: "desactivar"; sede: RespuestaUbicacion };

/** HU06: servicios (proyectos) y sedes/zonas autorizadas (ubicaciones) dentro de cada uno. */
export function PaginaSedes() {
  const { token } = useSesion();
  const [proyectos, setProyectos] = useState<RespuestaProyecto[]>([]);
  const [ubicaciones, setUbicaciones] = useState<RespuestaUbicacion[]>([]);
  const [cargando, setCargando] = useState(true);

  const [formularioProyecto, setFormularioProyecto] = useState(PROYECTO_VACIO);
  const [errorProyecto, setErrorProyecto] = useState<string | null>(null);
  const [guardandoProyecto, setGuardandoProyecto] = useState(false);
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [ejecutando, setEjecutando] = useState(false);

  function cargarTodo() {
    if (!token) return;
    setCargando(true);
    Promise.all([servicioProyectos.listar(token), servicioUbicaciones.listar(token)])
      .then(([p, u]) => {
        setProyectos(p);
        setUbicaciones(u);
      })
      .finally(() => setCargando(false));
  }

  useEffect(cargarTodo, [token]);

  async function manejarCrearProyecto(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;
    setErrorProyecto(null);
    setGuardandoProyecto(true);
    try {
      await servicioProyectos.registrar(token, formularioProyecto);
      setFormularioProyecto(PROYECTO_VACIO);
      cargarTodo();
    } catch (err) {
      setErrorProyecto(err instanceof ErrorHttp ? err.message : "No se pudo guardar el servicio");
    } finally {
      setGuardandoProyecto(false);
    }
  }

  function cerrarDialogo() {
    setDialogo(null);
    setErrorAccion(null);
  }

  function terminar(mensaje: string) {
    cerrarDialogo();
    setAviso(mensaje);
    cargarTodo();
  }

  async function ejecutar(accion: () => Promise<unknown>, mensaje: string, mensajeError: string) {
    setEjecutando(true);
    setErrorAccion(null);
    try {
      await accion();
      terminar(mensaje);
    } catch (err) {
      setErrorAccion(err instanceof ErrorHttp ? err.message : mensajeError);
    } finally {
      setEjecutando(false);
    }
  }

  function opcionesDe(sede: RespuestaUbicacion): OpcionMenu[] {
    if (!token) return [];
    if (!sede.activo) {
      return [
        {
          etiqueta: "Reactivar",
          alElegir: () =>
            ejecutar(() => servicioUbicaciones.reactivar(token, sede.id), `Se reactivó ${sede.nombre}.`, "No se pudo reactivar la sede"),
        },
      ];
    }
    return [
      { etiqueta: "Editar zona", alElegir: () => setDialogo({ tipo: "editar", sede }) },
      { etiqueta: "Desactivar", peligro: true, alElegir: () => setDialogo({ tipo: "desactivar", sede }) },
    ];
  }

  function nombreProyecto(proyectoId: number) {
    return proyectos.find((p) => p.id === proyectoId)?.nombre ?? `#${proyectoId}`;
  }

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center shrink-0">
        <h1 className="text-lg font-bold text-texto">Sedes y servicios</h1>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-8">
        <section className="flex flex-col gap-4">
          <h2 className="text-sm font-bold text-textoSuave uppercase tracking-wide">Servicios (proyectos)</h2>

          <form onSubmit={manejarCrearProyecto} className="bg-superficie rounded-md shadow-tarjeta p-5 grid grid-cols-1 sm:grid-cols-4 gap-4 items-end">
            <Campo etiqueta="Nombre">
              <input required value={formularioProyecto.nombre} onChange={(e) => setFormularioProyecto({ ...formularioProyecto, nombre: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Cliente">
              <input required value={formularioProyecto.cliente} onChange={(e) => setFormularioProyecto({ ...formularioProyecto, cliente: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Fecha de inicio">
              <input required type="date" value={formularioProyecto.fechaInicio} onChange={(e) => setFormularioProyecto({ ...formularioProyecto, fechaInicio: e.target.value })} className="campo" />
            </Campo>
            <button
              type="submit"
              disabled={guardandoProyecto}
              className="h-11 px-5 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
            >
              {guardandoProyecto ? "Guardando…" : "+ Servicio"}
            </button>
            {errorProyecto && (
              <p className="sm:col-span-4 text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
                {errorProyecto}
              </p>
            )}
          </form>

          <Tabla
            cargando={cargando}
            vacio="No hay servicios registrados todavía."
            columnas={["Nombre", "Cliente", "Inicio", "Estado"]}
            filas={proyectos.map((p) => [
              p.nombre,
              p.cliente,
              p.fechaInicio,
              <EstadoPill key="estado" activo={p.activo} />,
            ])}
          />
        </section>

        <section className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold text-textoSuave uppercase tracking-wide">Sedes y zonas autorizadas</h2>
            <button
              onClick={() => setDialogo({ tipo: "crear" })}
              disabled={proyectos.length === 0}
              title={proyectos.length === 0 ? "Registre primero un servicio" : undefined}
              className="h-9 px-4 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
            >
              + Nueva sede
            </button>
          </div>

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
          {errorAccion && dialogo === null && (
            <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
              {errorAccion}
            </p>
          )}

          <Tabla
            cargando={cargando}
            vacio="No hay sedes registradas todavía."
            columnas={["Sede", "Servicio", "Centro de la zona", "Radio (m)", "Estado", ""]}
            filas={ubicaciones.map((u) => [
              <div key="sede">
                <p className="font-medium">{u.nombre}</p>
                {u.direccionReferencia && <p className="text-xs text-textoSuave">{u.direccionReferencia}</p>}
              </div>,
              nombreProyecto(u.proyectoId),
              <span key="centro" className="font-mono text-xs">
                {formatearCoordenadas(u.latitud, u.longitud)}
              </span>,
              String(u.radioToleranciaMetros),
              <EstadoPill key="estado" activo={u.activo} />,
              <div key="acciones" className="text-right">
                <MenuAcciones etiqueta={`Acciones de la sede ${u.nombre}`} opciones={opcionesDe(u)} />
              </div>,
            ])}
          />
        </section>
      </main>
      {dialogo?.tipo === "crear" && token && (
        <Modal titulo="Nueva sede" ancho="lg" onCerrar={cerrarDialogo}>
          <FormularioSede
            token={token}
            proyectos={proyectos}
            ubicaciones={ubicaciones}
            alCancelar={cerrarDialogo}
            alGuardar={(creada) => terminar(`Se registró la sede ${creada.nombre}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "editar" && token && (
        <Modal titulo={`Editar zona de ${dialogo.sede.nombre}`} ancho="lg" onCerrar={cerrarDialogo}>
          <FormularioSede
            token={token}
            sede={dialogo.sede}
            proyectos={proyectos}
            ubicaciones={ubicaciones}
            alCancelar={cerrarDialogo}
            alGuardar={(guardada) => terminar(`Se guardaron los cambios de ${guardada.nombre}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "desactivar" && token && (
        <Modal titulo="Desactivar sede" onCerrar={cerrarDialogo}>
          <p className="text-sm text-texto">
            <strong>{dialogo.sede.nombre}</strong> ({nombreProyecto(dialogo.sede.proyectoId)}) dejará de estar disponible
            para nuevas asignaciones. Las marcaciones ya registradas se conservan y puede reactivarla cuando quiera. Si
            hay técnicos asignados a esta sede, primero hay que moverlos o quitarlos.
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
              onClick={() =>
                ejecutar(
                  () => servicioUbicaciones.desactivar(token, dialogo.sede.id),
                  `Se desactivó ${dialogo.sede.nombre}.`,
                  "No se pudo desactivar la sede",
                )
              }
              className="h-10 rounded-sm bg-peligro px-4 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-60"
            >
              {ejecutando ? "Desactivando…" : "Desactivar"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}

