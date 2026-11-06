import { useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { ErrorHttp } from "../services/clienteApi";
import { servicioProyectos } from "../services/servicioProyectos";
import type { RespuestaProyecto } from "../types/api";
import {
  aSolicitudServicio,
  hayCambiosServicio,
  validarServicio,
  valoresDeServicio,
  valoresServicioVacios,
  type ValoresServicio,
} from "./reglas";

interface Props {
  token: string;
  /** Si viene, se corrige ese servicio; si no, se registra uno nuevo. */
  proyecto?: RespuestaProyecto;
  alGuardar: (proyecto: RespuestaProyecto) => void;
  alCancelar: () => void;
}

/** HU06: alta y corrección de un servicio (proyecto/cliente). La fecha de fin es opcional. */
export function FormularioServicio({ token, proyecto, alGuardar, alCancelar }: Props) {
  const editando = proyecto !== undefined;
  const [valores, setValores] = useState<ValoresServicio>(() =>
    proyecto ? valoresDeServicio(proyecto) : valoresServicioVacios(),
  );
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cambiar(parcial: Partial<ValoresServicio>) {
    setValores((actuales) => ({ ...actuales, ...parcial }));
    setError(null);
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const problema = validarServicio(valores);
    if (problema) {
      setError(problema);
      return;
    }
    if (proyecto && !hayCambiosServicio(proyecto, valores)) {
      setError("No hay cambios que guardar.");
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const solicitud = aSolicitudServicio(valores);
      const guardado = proyecto
        ? await servicioProyectos.editar(token, proyecto.id, solicitud)
        : await servicioProyectos.registrar(token, solicitud);
      alGuardar(guardado);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo guardar el servicio");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={guardar} noValidate className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Campo etiqueta="Nombre del servicio">
          <input value={valores.nombre} onChange={(e) => cambiar({ nombre: e.target.value })} maxLength={150} className="campo" />
        </Campo>
        <Campo etiqueta="Cliente">
          <input value={valores.cliente} onChange={(e) => cambiar({ cliente: e.target.value })} maxLength={150} className="campo" />
        </Campo>
        <Campo etiqueta="Fecha de inicio">
          <input type="date" value={valores.fechaInicio} onChange={(e) => cambiar({ fechaInicio: e.target.value })} className="campo" />
        </Campo>
        <Campo etiqueta="Fecha de fin (opcional)">
          <input type="date" value={valores.fechaFin} onChange={(e) => cambiar({ fechaFin: e.target.value })} className="campo" />
        </Campo>
      </div>

      {error && (
        <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
          {error}
        </p>
      )}

      <div className="flex justify-end gap-3">
        <button type="button" onClick={alCancelar} className="h-10 rounded-sm border border-borde px-4 text-sm font-semibold text-texto hover:bg-fondo">
          Cancelar
        </button>
        <button
          type="submit"
          disabled={guardando}
          className="h-10 rounded-sm bg-primario px-5 text-sm font-semibold text-white hover:bg-primarioOscuro disabled:opacity-60"
        >
          {guardando ? "Guardando…" : editando ? "Guardar cambios" : "Registrar servicio"}
        </button>
      </div>
    </form>
  );
}
