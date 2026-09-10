import { useEffect, useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
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

const FORMULARIO_VACIO = {
  usuarioId: "",
  proyectoId: "",
  ubicacionId: "",
  horarioId: "",
  fechaInicio: "",
  fechaFin: "",
};

/** HU08: asignación de técnicos a un servicio, sede y turno, en un rango de fechas. */
export function PaginaAsignaciones() {
  const { token } = useSesion();
  const [asignaciones, setAsignaciones] = useState<RespuestaAsignacion[]>([]);
  const [tecnicos, setTecnicos] = useState<RespuestaUsuario[]>([]);
  const [proyectos, setProyectos] = useState<RespuestaProyecto[]>([]);
  const [ubicaciones, setUbicaciones] = useState<RespuestaUbicacion[]>([]);
  const [horarios, setHorarios] = useState<RespuestaHorario[]>([]);
  const [cargando, setCargando] = useState(true);
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargarTodo() {
    if (!token) return;
    setCargando(true);
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
      .finally(() => setCargando(false));
  }

  useEffect(cargarTodo, [token]);

  const ubicacionesDelProyecto = ubicaciones.filter((u) => String(u.proyectoId) === formulario.proyectoId);

  async function manejarCrear(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;
    setError(null);
    setGuardando(true);
    try {
      await servicioAsignaciones.registrar(token, {
        usuarioId: Number(formulario.usuarioId),
        proyectoId: Number(formulario.proyectoId),
        ubicacionId: Number(formulario.ubicacionId),
        horarioId: Number(formulario.horarioId),
        fechaInicio: formulario.fechaInicio,
        fechaFin: formulario.fechaFin || undefined,
      });
      setFormulario(FORMULARIO_VACIO);
      cargarTodo();
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo guardar la asignación");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center shrink-0">
        <h1 className="text-lg font-bold text-texto">Asignaciones</h1>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-6">
        <form onSubmit={manejarCrear} className="bg-superficie rounded-md shadow-tarjeta p-5 grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Campo etiqueta="Técnico">
            <select required value={formulario.usuarioId} onChange={(e) => setFormulario({ ...formulario, usuarioId: e.target.value })} className="campo">
              <option value="" disabled>
                Selecciona un técnico
              </option>
              {tecnicos.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nombres} {t.apellidos}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Servicio">
            <select
              required
              value={formulario.proyectoId}
              onChange={(e) => setFormulario({ ...formulario, proyectoId: e.target.value, ubicacionId: "" })}
              className="campo"
            >
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
          <Campo etiqueta="Sede">
            <select
              required
              value={formulario.ubicacionId}
              onChange={(e) => setFormulario({ ...formulario, ubicacionId: e.target.value })}
              disabled={!formulario.proyectoId}
              className="campo disabled:opacity-60"
            >
              <option value="" disabled>
                {formulario.proyectoId ? "Selecciona una sede" : "Primero elige un servicio"}
              </option>
              {ubicacionesDelProyecto.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.nombre}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Turno">
            <select required value={formulario.horarioId} onChange={(e) => setFormulario({ ...formulario, horarioId: e.target.value })} className="campo">
              <option value="" disabled>
                Selecciona un turno
              </option>
              {horarios.map((h) => (
                <option key={h.id} value={h.id}>
                  {h.nombre}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Fecha de inicio">
            <input required type="date" value={formulario.fechaInicio} onChange={(e) => setFormulario({ ...formulario, fechaInicio: e.target.value })} className="campo" />
          </Campo>
          <Campo etiqueta="Fecha de fin (opcional)">
            <input type="date" value={formulario.fechaFin} onChange={(e) => setFormulario({ ...formulario, fechaFin: e.target.value })} className="campo" />
          </Campo>

          {error && (
            <p className="sm:col-span-3 text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
              {error}
            </p>
          )}

          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={guardando}
              className="h-10 px-5 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
            >
              {guardando ? "Guardando…" : "+ Asignación"}
            </button>
          </div>
        </form>

        <Tabla
          cargando={cargando}
          vacio="No hay asignaciones registradas todavía."
          columnas={["Técnico", "Servicio", "Sede", "Turno", "Desde", "Hasta", "Estado"]}
          filas={asignaciones.map((a) => [
            a.nombreUsuario,
            a.nombreProyecto,
            a.nombreUbicacion,
            a.nombreHorario,
            a.fechaInicio,
            a.fechaFin ?? "—",
            <span
              key="estado"
              className={`text-xs font-semibold px-2 py-1 rounded-full whitespace-nowrap ${
                a.estado === "VIGENTE" ? "bg-exitoFondo text-exitoTexto" : "bg-primario/10 text-primario"
              }`}
            >
              {a.estado === "VIGENTE" ? "Vigente" : "Programada"}
            </span>,
          ])}
        />
      </main>
    </>
  );
}
