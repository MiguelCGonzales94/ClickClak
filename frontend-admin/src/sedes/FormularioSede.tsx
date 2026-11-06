import { useMemo, useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { DireccionPunto } from "../components/DireccionPunto";
import { ErrorHttp } from "../services/clienteApi";
import { servicioUbicaciones } from "../services/servicioUbicaciones";
import type { RespuestaProyecto, RespuestaUbicacion } from "../types/api";
import { MapaZona } from "./MapaZona";
import {
  hayCambios,
  interpretarCoordenadas,
  puntoDeValores,
  radioDeValores,
  redondear,
  sedesVecinas,
  validarSede,
  valoresDeSede,
  valoresVacios,
  type Punto,
  type ValoresSede,
} from "./reglas";

interface Props {
  token: string;
  /** Si viene, se corrige esa sede (el servicio no se puede cambiar); si no, se registra una nueva. */
  sede?: RespuestaUbicacion;
  proyectos: RespuestaProyecto[];
  /** Todas las sedes, para dibujar las vecinas del mismo servicio. */
  ubicaciones: RespuestaUbicacion[];
  alGuardar: (sede: RespuestaUbicacion) => void;
  alCancelar: () => void;
}

/**
 * HU06: alta y corrección de una sede. La zona se marca en el mapa (clic o arrastre del marcador) o se
 * escribe/pega como coordenadas; el radio es la tolerancia con la que el servidor valida las marcaciones.
 */
export function FormularioSede({ token, sede, proyectos, ubicaciones, alGuardar, alCancelar }: Props) {
  const editando = sede !== undefined;
  const [valores, setValores] = useState<ValoresSede>(() => (sede ? valoresDeSede(sede) : valoresVacios()));
  const [pegado, setPegado] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const punto = useMemo(() => puntoDeValores(valores), [valores]);
  const radio = radioDeValores(valores.radioToleranciaMetros);
  const vecinas = useMemo(
    () => sedesVecinas(ubicaciones, valores.proyectoId, sede?.id),
    [ubicaciones, valores.proyectoId, sede?.id],
  );

  function cambiar(parcial: Partial<ValoresSede>) {
    setValores((actuales) => ({ ...actuales, ...parcial }));
    setError(null);
  }

  function marcar(nuevo: Punto) {
    setAviso(null);
    cambiar({ latitud: String(redondear(nuevo.latitud)), longitud: String(redondear(nuevo.longitud)) });
  }

  function aplicarPegado(texto: string) {
    setPegado(texto);
    const interpretado = interpretarCoordenadas(texto);
    if (interpretado) marcar(interpretado);
  }

  function usarMiUbicacion() {
    setAviso(null);
    if (!navigator.geolocation) {
      setAviso("Este navegador no ofrece ubicación. Marque la zona en el mapa.");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (posicion) => marcar({ latitud: posicion.coords.latitude, longitud: posicion.coords.longitude }),
      () => setAviso("No se pudo obtener su ubicación. Marque la zona en el mapa."),
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const problema = validarSede(valores, editando);
    if (problema) {
      setError(problema);
      return;
    }
    if (sede && !hayCambios(sede, valores)) {
      setError("No hay cambios que guardar.");
      return;
    }
    const centro = puntoDeValores(valores)!;
    const cuerpo = {
      nombre: valores.nombre.trim(),
      direccionReferencia: valores.direccionReferencia.trim() || undefined,
      latitud: centro.latitud,
      longitud: centro.longitud,
      radioToleranciaMetros: radioDeValores(valores.radioToleranciaMetros)!,
    };
    setGuardando(true);
    setError(null);
    try {
      const guardada = sede
        ? await servicioUbicaciones.editar(token, sede.id, cuerpo)
        : await servicioUbicaciones.registrar(token, { ...cuerpo, proyectoId: Number(valores.proyectoId) });
      alGuardar(guardada);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo guardar la sede");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={guardar} noValidate className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Campo etiqueta="Servicio">
          <select
            required
            disabled={editando}
            value={valores.proyectoId}
            onChange={(e) => cambiar({ proyectoId: e.target.value })}
            className="campo disabled:bg-fondo disabled:text-textoSuave"
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
        <Campo etiqueta="Nombre de la sede">
          <input value={valores.nombre} onChange={(e) => cambiar({ nombre: e.target.value })} maxLength={150} className="campo" />
        </Campo>
        <div className="sm:col-span-2">
          <Campo etiqueta="Referencia (opcional)">
            <input
              value={valores.direccionReferencia}
              onChange={(e) => cambiar({ direccionReferencia: e.target.value })}
              maxLength={255}
              placeholder="Dirección o indicación para ubicarla"
              className="campo"
            />
          </Campo>
        </div>
      </div>

      <section className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-bold text-texto">Zona autorizada</h3>
          <button
            type="button"
            onClick={usarMiUbicacion}
            className="h-8 rounded-sm border border-borde px-3 text-xs font-semibold text-texto hover:bg-fondo"
          >
            Usar mi ubicación
          </button>
        </div>
        <DireccionPunto
          latitud={punto?.latitud ?? null}
          longitud={punto?.longitud ?? null}
          etiqueta="Dirección del punto"
          alUsar={(direccion) => cambiar({ direccionReferencia: direccion.slice(0, 255) })}
        />
        <p className="text-xs text-textoSuave">
          Haga clic en el mapa para colocar el centro y arrastre el marcador para afinarlo. El círculo es la zona donde
          se acepta una marcación.
        </p>
        <MapaZona punto={punto} radioMetros={radio} vecinas={vecinas} alMarcar={marcar} />
        {punto === null && <p className="text-xs text-advertenciaTexto">Aún no hay zona marcada.</p>}
        {aviso && (
          <p role="status" className="text-xs text-advertenciaTexto">
            {aviso}
          </p>
        )}
      </section>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-4">
        <div className="sm:col-span-4">
          <Campo etiqueta="Pegar coordenadas (opcional)">
            <input
              value={pegado}
              onChange={(e) => aplicarPegado(e.target.value)}
              placeholder="-12.046374, -77.042793"
              className="campo"
              inputMode="decimal"
            />
          </Campo>
          {pegado.trim() !== "" && interpretarCoordenadas(pegado) === null && (
            <p className="mt-1 text-xs text-peligroTexto">No se reconocen como «latitud, longitud».</p>
          )}
        </div>
        <div className="sm:col-span-2 grid grid-cols-2 gap-4">
          <Campo etiqueta="Latitud">
            <input type="number" step="any" value={valores.latitud} onChange={(e) => cambiar({ latitud: e.target.value })} className="campo" />
          </Campo>
          <Campo etiqueta="Longitud">
            <input type="number" step="any" value={valores.longitud} onChange={(e) => cambiar({ longitud: e.target.value })} className="campo" />
          </Campo>
        </div>
        <div className="sm:col-span-2">
          <Campo etiqueta={`Radio de tolerancia: ${radio ?? "—"} m`}>
            <div className="flex items-center gap-3">
              <input
                type="range"
                min={10}
                max={1000}
                step={10}
                value={Math.min(Math.max(radio ?? 150, 10), 1000)}
                onChange={(e) => cambiar({ radioToleranciaMetros: e.target.value })}
                aria-label="Ajustar el radio de tolerancia"
                className="h-11 flex-1 accent-primario"
              />
              <input
                type="number"
                min={1}
                max={5000}
                value={valores.radioToleranciaMetros}
                onChange={(e) => cambiar({ radioToleranciaMetros: e.target.value })}
                aria-label="Radio de tolerancia en metros"
                className="campo w-24"
              />
            </div>
          </Campo>
        </div>
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
          {guardando ? "Guardando…" : editando ? "Guardar cambios" : "Registrar sede"}
        </button>
      </div>
    </form>
  );
}
