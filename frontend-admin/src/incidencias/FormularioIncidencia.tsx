import { useEffect, useRef, useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { ErrorHttp } from "../services/clienteApi";
import { servicioIncidencias } from "../services/servicioIncidencias";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaIncidencia, RespuestaUsuario, TipoIncidencia } from "../types/api";
import { fechaMaximaDeEvento, ETIQUETA_TIPO, TIPOS } from "./reglas";

interface Props {
  usuarios: RespuestaUsuario[];
  onCerrar: () => void;
  onCreada: (incidencia: RespuestaIncidencia) => void;
}

const LIMITE_DESCRIPCION = 2000;

/** Alta de una incidencia a nombre de un colaborador, por el personal de revisión. */
export function FormularioIncidencia({ usuarios, onCerrar, onCreada }: Props) {
  const { token } = useSesion();
  const [usuarioId, setUsuarioId] = useState("");
  const [tipo, setTipo] = useState<TipoIncidencia>("AUSENCIA");
  const [fechaEvento, setFechaEvento] = useState("");
  const [descripcion, setDescripcion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const primerCampo = useRef<HTMLSelectElement>(null);

  const alCerrar = useRef(onCerrar);
  alCerrar.current = onCerrar;
  useEffect(() => {
    primerCampo.current?.focus();
    function alPulsarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") alCerrar.current();
    }
    document.addEventListener("keydown", alPulsarTecla);
    return () => document.removeEventListener("keydown", alPulsarTecla);
  }, []);

  const fechaMaxima = fechaMaximaDeEvento(tipo);

  function cambiarTipo(nuevo: TipoIncidencia) {
    setTipo(nuevo);
    // Si se pasa de Permiso a otro tipo, una fecha futura deja de ser válida.
    const maxima = fechaMaximaDeEvento(nuevo);
    if (maxima && fechaEvento > maxima) setFechaEvento("");
  }

  async function manejarEnviar(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;
    setError(null);
    setGuardando(true);
    try {
      const creada = await servicioIncidencias.registrar(token, {
        usuarioId: Number(usuarioId),
        tipo,
        fechaEvento,
        descripcion: descripcion.trim(),
      });
      onCreada(creada);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo registrar la incidencia");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-[#061229]/45" onClick={onCerrar} aria-hidden="true" />

      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-formulario-incidencia"
        onSubmit={manejarEnviar}
        // El error del servidor (por ejemplo un 409) describe los datos anteriores: se retira al editar cualquier campo.
        onChange={() => setError(null)}
        className="relative w-full max-w-lg bg-superficie rounded-md shadow-tarjeta p-6 flex flex-col gap-4"
      >
        <div className="flex items-center justify-between">
          <h2 id="titulo-formulario-incidencia" className="text-base font-bold text-texto">
            Registrar incidencia
          </h2>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar el formulario"
            className="h-9 w-9 rounded-sm text-textoSuave hover:bg-fondo hover:text-texto transition-colors text-xl leading-none"
          >
            ×
          </button>
        </div>

        <Campo etiqueta="Colaborador">
          <select
            ref={primerCampo}
            required
            value={usuarioId}
            onChange={(evento) => setUsuarioId(evento.target.value)}
            className="campo"
          >
            <option value="" disabled>
              Selecciona un colaborador
            </option>
            {usuarios.map((usuario) => (
              <option key={usuario.id} value={usuario.id}>
                {usuario.nombres} {usuario.apellidos}
              </option>
            ))}
          </select>
        </Campo>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Campo etiqueta="Tipo">
            <select value={tipo} onChange={(evento) => cambiarTipo(evento.target.value as TipoIncidencia)} className="campo">
              {TIPOS.map((valor) => (
                <option key={valor} value={valor}>
                  {ETIQUETA_TIPO[valor]}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Fecha del evento">
            <input
              required
              type="date"
              value={fechaEvento}
              max={fechaMaxima}
              onChange={(evento) => setFechaEvento(evento.target.value)}
              className="campo"
            />
          </Campo>
        </div>
        {tipo !== "PERMISO" && (
          <p className="-mt-2 text-xs text-textoSuave">Solo un permiso puede registrarse con una fecha futura.</p>
        )}

        <Campo etiqueta="Descripción">
          <textarea
            required
            value={descripcion}
            onChange={(evento) => setDescripcion(evento.target.value)}
            maxLength={LIMITE_DESCRIPCION}
            rows={4}
            className="px-3 py-2 rounded-sm border border-borde text-sm text-texto bg-superficie focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario resize-none"
          />
          <span className="text-xs text-textoSuave text-right">
            {descripcion.length} / {LIMITE_DESCRIPCION}
          </span>
        </Campo>

        {error && (
          <p role="alert" className="text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCerrar}
            className="h-10 px-4 rounded-sm border border-borde text-sm font-semibold text-texto hover:bg-fondo transition-colors"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={guardando || !descripcion.trim()}
            className="h-10 px-5 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
          >
            {guardando ? "Guardando…" : "Registrar"}
          </button>
        </div>
      </form>
    </div>
  );
}
