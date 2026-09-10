import { useEffect, useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioHorarios } from "../services/servicioHorarios";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaHorario } from "../types/api";

const FORMULARIO_VACIO = {
  nombre: "",
  horaInicio: "08:00",
  horaFin: "17:00",
  toleranciaMinutos: "10",
};

/** HU07: turnos y horarios de trabajo. */
export function PaginaTurnos() {
  const { token } = useSesion();
  const [horarios, setHorarios] = useState<RespuestaHorario[]>([]);
  const [cargando, setCargando] = useState(true);
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargarHorarios() {
    if (!token) return;
    setCargando(true);
    servicioHorarios
      .listar(token)
      .then(setHorarios)
      .finally(() => setCargando(false));
  }

  useEffect(cargarHorarios, [token]);

  async function manejarCrear(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;
    setError(null);
    setGuardando(true);
    try {
      await servicioHorarios.registrar(token, {
        nombre: formulario.nombre,
        horaInicio: `${formulario.horaInicio}:00`,
        horaFin: `${formulario.horaFin}:00`,
        toleranciaMinutos: Number(formulario.toleranciaMinutos),
      });
      setFormulario(FORMULARIO_VACIO);
      cargarHorarios();
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo guardar el turno");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center shrink-0">
        <h1 className="text-lg font-bold text-texto">Turnos</h1>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-6">
        <form onSubmit={manejarCrear} className="bg-superficie rounded-md shadow-tarjeta p-5 grid grid-cols-1 sm:grid-cols-4 gap-4 items-end">
          <Campo etiqueta="Nombre del turno">
            <input required value={formulario.nombre} onChange={(e) => setFormulario({ ...formulario, nombre: e.target.value })} placeholder="Turno diurno" className="campo" />
          </Campo>
          <Campo etiqueta="Hora de inicio">
            <input required type="time" value={formulario.horaInicio} onChange={(e) => setFormulario({ ...formulario, horaInicio: e.target.value })} className="campo" />
          </Campo>
          <Campo etiqueta="Hora de fin">
            <input required type="time" value={formulario.horaFin} onChange={(e) => setFormulario({ ...formulario, horaFin: e.target.value })} className="campo" />
          </Campo>
          <Campo etiqueta="Tolerancia (min)">
            <input required type="number" min={0} value={formulario.toleranciaMinutos} onChange={(e) => setFormulario({ ...formulario, toleranciaMinutos: e.target.value })} className="campo" />
          </Campo>

          {error && (
            <p className="sm:col-span-4 text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
              {error}
            </p>
          )}

          <div className="sm:col-span-4">
            <button
              type="submit"
              disabled={guardando}
              className="h-10 px-5 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
            >
              {guardando ? "Guardando…" : "+ Turno"}
            </button>
          </div>
        </form>

        <Tabla
          cargando={cargando}
          vacio="No hay turnos registrados todavía."
          columnas={["Nombre", "Inicio", "Fin", "Tolerancia"]}
          filas={horarios.map((h) => [h.nombre, h.horaInicio, h.horaFin, `${h.toleranciaMinutos} min`])}
        />
      </main>
    </>
  );
}
