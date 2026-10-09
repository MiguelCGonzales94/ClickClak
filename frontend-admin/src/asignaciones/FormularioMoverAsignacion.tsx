import { useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAsignaciones } from "../services/servicioAsignaciones";
import type { RespuestaAsignacion, RespuestaHorario, RespuestaProyecto, RespuestaUbicacion } from "../types/api";
import {
  describirTraslado,
  fechaDeCambioInicial,
  fechaMaximaDeCambio,
  fechaMinimaDeCambio,
  sedesDelServicio,
  validarFechaDeCambio,
} from "./reglas";

interface Props {
  token: string;
  asignacion: RespuestaAsignacion;
  proyectos: RespuestaProyecto[];
  ubicaciones: RespuestaUbicacion[];
  horarios: RespuestaHorario[];
  hoy: string;
  alGuardar: (nueva: RespuestaAsignacion) => void;
  alCancelar: () => void;
}

/**
 * HU08: "quitar de una sede y poner en otra" en un solo paso. La asignación actual termina el día
 * anterior a la fecha de cambio y nace la nueva, que hereda la fecha de fin; si algo falla, el
 * técnico no queda sin asignación porque el servidor lo hace en una transacción.
 */
export function FormularioMoverAsignacion({ token, asignacion, proyectos, ubicaciones, horarios, hoy, alGuardar, alCancelar }: Props) {
  const [proyectoId, setProyectoId] = useState(String(asignacion.proyectoId));
  const [ubicacionId, setUbicacionId] = useState("");
  const [horarioId, setHorarioId] = useState(String(asignacion.horarioId));
  const [fechaCambio, setFechaCambio] = useState(() => fechaDeCambioInicial(asignacion, hoy));
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  // La sede actual no se ofrece: moverlo a donde ya está no tiene sentido (para el turno o las fechas, se edita).
  const sedesPosibles = sedesDelServicio(ubicaciones, proyectoId).filter((u) => u.id !== asignacion.ubicacionId);
  const sedeElegida = ubicaciones.find((u) => String(u.id) === ubicacionId);

  async function manejarEnvio(evento: FormEvent) {
    evento.preventDefault();
    const problema = validarFechaDeCambio(asignacion, fechaCambio);
    if (problema) {
      setError(problema);
      return;
    }
    setGuardando(true);
    try {
      const nueva = await servicioAsignaciones.mover(token, asignacion.id, {
        ubicacionId: Number(ubicacionId),
        proyectoId: Number(proyectoId),
        horarioId: Number(horarioId),
        fechaCambio,
      });
      alGuardar(nueva);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo mover la asignación");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={manejarEnvio} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <p className="sm:col-span-2 text-sm text-texto">
        <span className="font-semibold">{asignacion.nombreUsuario}</span> está en{" "}
        <span className="font-semibold">{asignacion.nombreUbicacion}</span> ({asignacion.nombreProyecto}).
      </p>

      <Campo etiqueta="Servicio de la sede nueva">
        <select
          value={proyectoId}
          onChange={(e) => {
            setProyectoId(e.target.value);
            setUbicacionId("");
            setError(null);
          }}
          className="campo"
        >
          {proyectos.map((p) => (
            <option key={p.id} value={p.id}>
              {p.nombre}
            </option>
          ))}
        </select>
      </Campo>
      <Campo etiqueta="Sede nueva">
        <select
          required
          value={ubicacionId}
          onChange={(e) => {
            setUbicacionId(e.target.value);
            setError(null);
          }}
          className="campo"
        >
          <option value="" disabled>
            {sedesPosibles.length === 0 ? "Este servicio no tiene otras sedes" : "Selecciona una sede"}
          </option>
          {sedesPosibles.map((u) => (
            <option key={u.id} value={u.id}>
              {u.nombre}
            </option>
          ))}
        </select>
      </Campo>
      <Campo etiqueta="Turno">
        <select value={horarioId} onChange={(e) => setHorarioId(e.target.value)} className="campo">
          {horarios.map((h) => (
            <option key={h.id} value={h.id}>
              {h.nombre}
            </option>
          ))}
        </select>
      </Campo>
      <Campo etiqueta="Fecha de cambio">
        <input
          required
          type="date"
          value={fechaCambio}
          min={fechaMinimaDeCambio(asignacion)}
          max={fechaMaximaDeCambio(asignacion)}
          onChange={(e) => {
            setFechaCambio(e.target.value);
            setError(null);
          }}
          className="campo"
        />
      </Campo>

      {ubicacionId && fechaCambio && (
        <p className="sm:col-span-2 rounded-sm border border-borde bg-fondo px-3 py-2 text-sm text-texto">
          {describirTraslado(asignacion, fechaCambio, sedeElegida?.nombre ?? "")}
        </p>
      )}

      {error && (
        <p role="alert" className="sm:col-span-2 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
          {error}
        </p>
      )}

      <div className="flex gap-3 sm:col-span-2">
        <button
          type="submit"
          disabled={guardando || !ubicacionId}
          className="h-10 rounded-sm bg-primario px-5 text-sm font-semibold text-white transition-colors hover:bg-primarioOscuro disabled:opacity-60"
        >
          {guardando ? "Moviendo…" : "Mover a la sede nueva"}
        </button>
        <button
          type="button"
          onClick={alCancelar}
          className="h-10 rounded-sm border border-borde px-5 text-sm font-semibold text-texto hover:bg-fondo"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
