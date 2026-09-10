import { useEffect, useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { EstadoPill, Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioProyectos } from "../services/servicioProyectos";
import { servicioUbicaciones } from "../services/servicioUbicaciones";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaProyecto, RespuestaUbicacion } from "../types/api";

const PROYECTO_VACIO = { nombre: "", cliente: "", fechaInicio: "" };
const UBICACION_VACIA = {
  proyectoId: "",
  nombre: "",
  direccionReferencia: "",
  latitud: "",
  longitud: "",
  radioToleranciaMetros: "150",
};

/** HU06: servicios (proyectos) y sedes/zonas autorizadas (ubicaciones) dentro de cada uno. */
export function PaginaSedes() {
  const { token } = useSesion();
  const [proyectos, setProyectos] = useState<RespuestaProyecto[]>([]);
  const [ubicaciones, setUbicaciones] = useState<RespuestaUbicacion[]>([]);
  const [cargando, setCargando] = useState(true);

  const [formularioProyecto, setFormularioProyecto] = useState(PROYECTO_VACIO);
  const [formularioUbicacion, setFormularioUbicacion] = useState(UBICACION_VACIA);
  const [errorProyecto, setErrorProyecto] = useState<string | null>(null);
  const [errorUbicacion, setErrorUbicacion] = useState<string | null>(null);
  const [guardandoProyecto, setGuardandoProyecto] = useState(false);
  const [guardandoUbicacion, setGuardandoUbicacion] = useState(false);

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

  async function manejarCrearUbicacion(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;
    setErrorUbicacion(null);
    setGuardandoUbicacion(true);
    try {
      await servicioUbicaciones.registrar(token, {
        proyectoId: Number(formularioUbicacion.proyectoId),
        nombre: formularioUbicacion.nombre,
        direccionReferencia: formularioUbicacion.direccionReferencia || undefined,
        latitud: Number(formularioUbicacion.latitud),
        longitud: Number(formularioUbicacion.longitud),
        radioToleranciaMetros: Number(formularioUbicacion.radioToleranciaMetros),
      });
      setFormularioUbicacion(UBICACION_VACIA);
      cargarTodo();
    } catch (err) {
      setErrorUbicacion(err instanceof ErrorHttp ? err.message : "No se pudo guardar la sede");
    } finally {
      setGuardandoUbicacion(false);
    }
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
          <h2 className="text-sm font-bold text-textoSuave uppercase tracking-wide">Sedes y zonas autorizadas</h2>

          <form onSubmit={manejarCrearUbicacion} className="bg-superficie rounded-md shadow-tarjeta p-5 grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Campo etiqueta="Servicio">
              <select required value={formularioUbicacion.proyectoId} onChange={(e) => setFormularioUbicacion({ ...formularioUbicacion, proyectoId: e.target.value })} className="campo">
                <option value="" disabled>
                  Selecciona un servicio
                </option>
                {proyectos.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nombre}
                  </option>
                ))}
              </select>
            </Campo>
            <Campo etiqueta="Nombre de la sede">
              <input required value={formularioUbicacion.nombre} onChange={(e) => setFormularioUbicacion({ ...formularioUbicacion, nombre: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Referencia (opcional)">
              <input value={formularioUbicacion.direccionReferencia} onChange={(e) => setFormularioUbicacion({ ...formularioUbicacion, direccionReferencia: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Latitud">
              <input required type="number" step="any" value={formularioUbicacion.latitud} onChange={(e) => setFormularioUbicacion({ ...formularioUbicacion, latitud: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Longitud">
              <input required type="number" step="any" value={formularioUbicacion.longitud} onChange={(e) => setFormularioUbicacion({ ...formularioUbicacion, longitud: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Radio de tolerancia (m)">
              <input required type="number" min={1} value={formularioUbicacion.radioToleranciaMetros} onChange={(e) => setFormularioUbicacion({ ...formularioUbicacion, radioToleranciaMetros: e.target.value })} className="campo" />
            </Campo>

            {errorUbicacion && (
              <p className="sm:col-span-3 text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
                {errorUbicacion}
              </p>
            )}

            <div className="sm:col-span-3">
              <button
                type="submit"
                disabled={guardandoUbicacion}
                className="h-10 px-5 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
              >
                {guardandoUbicacion ? "Guardando…" : "+ Sede"}
              </button>
            </div>
          </form>

          <Tabla
            cargando={cargando}
            vacio="No hay sedes registradas todavía."
            columnas={["Sede", "Servicio", "Radio (m)", "Estado"]}
            filas={ubicaciones.map((u) => [
              u.nombre,
              nombreProyecto(u.proyectoId),
              String(u.radioToleranciaMetros),
              <EstadoPill key="estado" activo={u.activo} />,
            ])}
          />
        </section>
      </main>
    </>
  );
}

