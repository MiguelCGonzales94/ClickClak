import { useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAsignaciones } from "../services/servicioAsignaciones";
import type {
  RespuestaAsignacion,
  RespuestaHorario,
  RespuestaProyecto,
  RespuestaUbicacion,
  RespuestaUsuario,
} from "../types/api";
import { hayCambios, sedesDelServicio, validarFechas } from "./reglas";

interface Props {
  token: string;
  /** Si viene, se edita esa asignación (el técnico no se puede cambiar); si no, se crea una nueva. */
  asignacion?: RespuestaAsignacion;
  tecnicos: RespuestaUsuario[];
  proyectos: RespuestaProyecto[];
  ubicaciones: RespuestaUbicacion[];
  horarios: RespuestaHorario[];
  /** Técnico preseleccionado al crear (por ejemplo el que se está filtrando). */
  tecnicoInicialId?: string;
  alGuardar: (asignacion: RespuestaAsignacion) => void;
  alCancelar: () => void;
}

interface Valores {
  usuarioId: string;
  proyectoId: string;
  ubicacionId: string;
  horarioId: string;
  fechaInicio: string;
  fechaFin: string;
}

function valoresIniciales(asignacion: RespuestaAsignacion | undefined, tecnicoInicialId: string | undefined): Valores {
  return {
    usuarioId: asignacion ? String(asignacion.usuarioId) : (tecnicoInicialId ?? ""),
    proyectoId: asignacion ? String(asignacion.proyectoId) : "",
    ubicacionId: asignacion ? String(asignacion.ubicacionId) : "",
    horarioId: asignacion ? String(asignacion.horarioId) : "",
    fechaInicio: asignacion?.fechaInicio ?? "",
    fechaFin: asignacion?.fechaFin ?? "",
  };
}

/** HU08: alta y edición de una asignación. Un técnico puede tener varias sedes a la vez, pero no la misma dos veces. */
export function FormularioAsignacion({
  token,
  asignacion,
  tecnicos,
  proyectos,
  ubicaciones,
  horarios,
  tecnicoInicialId,
  alGuardar,
  alCancelar,
}: Props) {
  const editando = asignacion !== undefined;
  const [valores, setValores] = useState<Valores>(() => valoresIniciales(asignacion, tecnicoInicialId));
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function actualizar(parcial: Partial<Valores>) {
    setError(null);
    setValores((anteriores) => ({ ...anteriores, ...parcial }));
  }

  async function manejarEnvio(evento: FormEvent) {
    evento.preventDefault();
    const problema = validarFechas(valores.fechaInicio, valores.fechaFin);
    if (problema) {
      setError(problema);
      return;
    }

    const datos = {
      proyectoId: Number(valores.proyectoId),
      ubicacionId: Number(valores.ubicacionId),
      horarioId: Number(valores.horarioId),
      fechaInicio: valores.fechaInicio,
      fechaFin: valores.fechaFin || null,
    };
    if (editando && !hayCambios(asignacion, datos)) {
      setError("No hay cambios que guardar");
      return;
    }

    setGuardando(true);
    try {
      const guardada = editando
        ? await servicioAsignaciones.editar(token, asignacion.id, datos)
        : await servicioAsignaciones.registrar(token, { usuarioId: Number(valores.usuarioId), ...datos });
      alGuardar(guardada);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo guardar la asignación");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={manejarEnvio} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <div className="sm:col-span-2">
        {editando ? (
          <p className="text-sm text-texto">
            <span className="font-semibold">Técnico:</span> {asignacion.nombreUsuario}
          </p>
        ) : (
          <Campo etiqueta="Técnico">
            <select
              required
              value={valores.usuarioId}
              onChange={(e) => actualizar({ usuarioId: e.target.value })}
              className="campo"
            >
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
        )}
      </div>

      <Campo etiqueta="Servicio">
        <select
          required
          value={valores.proyectoId}
          onChange={(e) => actualizar({ proyectoId: e.target.value, ubicacionId: "" })}
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
          value={valores.ubicacionId}
          onChange={(e) => actualizar({ ubicacionId: e.target.value })}
          disabled={!valores.proyectoId}
          className="campo disabled:opacity-60"
        >
          <option value="" disabled>
            {valores.proyectoId ? "Selecciona una sede" : "Primero elige un servicio"}
          </option>
          {sedesDelServicio(ubicaciones, valores.proyectoId).map((u) => (
            <option key={u.id} value={u.id}>
              {u.nombre}
            </option>
          ))}
        </select>
      </Campo>
      <Campo etiqueta="Turno">
        <select required value={valores.horarioId} onChange={(e) => actualizar({ horarioId: e.target.value })} className="campo">
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
      <div className="hidden sm:block" />
      <Campo etiqueta="Fecha de inicio">
        <input
          required
          type="date"
          value={valores.fechaInicio}
          onChange={(e) => actualizar({ fechaInicio: e.target.value })}
          className="campo"
        />
      </Campo>
      <Campo etiqueta="Fecha de fin (opcional)">
        <input
          type="date"
          value={valores.fechaFin}
          min={valores.fechaInicio || undefined}
          onChange={(e) => actualizar({ fechaFin: e.target.value })}
          className="campo"
        />
      </Campo>

      <p className="sm:col-span-2 text-xs text-textoSuave">
        Un técnico puede tener varias sedes a la vez. Lo único que no se admite es la misma sede dos veces con fechas
        que se cruzan; para cambiarlo de sede use «Mover a otra sede».
      </p>

      {error && (
        <p role="alert" className="sm:col-span-2 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
          {error}
        </p>
      )}

      <div className="flex gap-3 sm:col-span-2">
        <button
          type="submit"
          disabled={guardando}
          className="h-10 rounded-sm bg-primario px-5 text-sm font-semibold text-white transition-colors hover:bg-primarioOscuro disabled:opacity-60"
        >
          {guardando ? "Guardando…" : editando ? "Guardar cambios" : "Crear asignación"}
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
