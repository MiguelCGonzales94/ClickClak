import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Icono } from "../components/Iconos";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAgenda } from "../services/servicioAgenda";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaAsignacion } from "../types/api";

type TipoMarca = "ENTRADA" | "SALIDA";

interface MarcaLocal {
  tipo: TipoMarca;
  hora: Date;
  latitud: number;
  longitud: number;
  precision: number | null;
}

/** HU09: agenda propia del colaborador — sedes, turnos y estado de sus asignaciones. */
export function PaginaAgenda() {
  const { token, perfil, cerrarSesion } = useSesion();
  const navegar = useNavigate();
  const [asignaciones, setAsignaciones] = useState<RespuestaAsignacion[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloj, setReloj] = useState(new Date());
  const [capturando, setCapturando] = useState<TipoMarca | null>(null);
  const [marcaLocal, setMarcaLocal] = useState<MarcaLocal | null>(null);
  const [errorMarcacion, setErrorMarcacion] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    servicioAgenda
      .obtenerMiAgenda(token)
      .then(setAsignaciones)
      .catch((err) =>
        setError(err instanceof ErrorHttp ? err.message : "No se pudo cargar la agenda"),
      )
      .finally(() => setCargando(false));
  }, [token]);

  useEffect(() => {
    const intervalo = window.setInterval(() => setReloj(new Date()), 1000);
    return () => window.clearInterval(intervalo);
  }, []);

  const asignacionPrincipal = useMemo(
    () => asignaciones.find((asignacion) => asignacion.estado === "VIGENTE") ?? asignaciones[0],
    [asignaciones],
  );

  async function manejarCerrarSesion() {
    // HU02: se intenta revocar el token en el servidor, pero el cierre local ocurre
    // igual aunque falle la llamada (sin conexión, token ya vencido, etc.) — el usuario
    // nunca debe quedar atrapado sin poder salir de su sesión.
    if (token) {
      try {
        await servicioAutenticacion.cerrarSesion(token);
      } catch {
        // Se ignora deliberadamente: ver comentario arriba.
      }
    }
    cerrarSesion();
    navegar("/login", { replace: true });
  }

  function solicitarMarcacion(tipo: TipoMarca) {
    setErrorMarcacion(null);

    if (!("geolocation" in navigator)) {
      setErrorMarcacion("Este dispositivo no permite capturar ubicación desde el navegador.");
      return;
    }

    setCapturando(tipo);
    navigator.geolocation.getCurrentPosition(
      (posicion) => {
        setMarcaLocal({
          tipo,
          hora: new Date(),
          latitud: posicion.coords.latitude,
          longitud: posicion.coords.longitude,
          precision: posicion.coords.accuracy,
        });
        setCapturando(null);
      },
      () => {
        setErrorMarcacion("No se pudo obtener la ubicación. Revisa el permiso del navegador.");
        setCapturando(null);
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 },
    );
  }

  const horaActual = new Intl.DateTimeFormat("es-PE", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(reloj);

  const fechaActual = new Intl.DateTimeFormat("es-PE", {
    weekday: "long",
    day: "2-digit",
    month: "long",
  }).format(reloj);

  return (
    <div className="min-h-screen bg-[#F3F5F9]">
      <header className="bg-[#10192C] text-white px-4 pt-5 pb-6">
        <div className="max-w-lg mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <div className="h-10 w-10 rounded-full bg-primario flex items-center justify-center font-bold shrink-0">
              {perfil?.nombres?.charAt(0)}
              {perfil?.apellidos?.charAt(0)}
            </div>
            <div className="min-w-0">
              <p className="text-xs text-slate-400">Técnico de campo</p>
              <p className="font-semibold truncate">
                {perfil?.nombres} {perfil?.apellidos}
              </p>
            </div>
          </div>
          <button
            onClick={manejarCerrarSesion}
            className="h-9 w-9 rounded-sm text-slate-300 hover:bg-white/10 hover:text-white transition-colors"
            aria-label="Cerrar sesión"
            title="Cerrar sesión"
          >
            <Icono nombre="cerrar" className="mx-auto h-5 w-5" />
          </button>
        </div>
      </header>

      <main className="max-w-lg mx-auto px-4 py-5 flex flex-col gap-4 -mt-3">
        <section className="bg-superficie rounded-sm border border-[#DEE5F0] shadow-[0_14px_34px_rgba(20,30,70,0.10)] p-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-[#66718A]">Marcación de asistencia</p>
              <p className="mt-3 text-[34px] leading-none font-bold text-[#061229]">{horaActual}</p>
              <p className="mt-2 text-sm capitalize text-[#66718A]">{fechaActual}</p>
            </div>
            <span className="h-11 w-11 rounded-sm bg-[#EAFBF7] text-[#0E9F8F] flex items-center justify-center">
              <Icono nombre="estado" className="h-6 w-6" />
            </span>
          </div>

          <div className="mt-5 rounded-sm border border-[#DCE3EF] bg-[#F8FAFD] p-4">
            <div className="flex items-start gap-3">
              <span className="h-9 w-9 rounded-sm bg-[#EEF3FF] text-[#3150D4] flex items-center justify-center shrink-0">
                <Icono nombre="pin" className="h-5 w-5" />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-[#061229]">
                  {asignacionPrincipal?.nombreProyecto ?? "Sin asignación vigente"}
                </p>
                <p className="mt-1 text-sm text-[#66718A]">
                  {asignacionPrincipal
                    ? `${asignacionPrincipal.nombreUbicacion} · ${asignacionPrincipal.nombreHorario}`
                    : "La agenda se actualizará cuando el supervisor asigne una sede."}
                </p>
              </div>
            </div>
          </div>

          {marcaLocal && (
            <div className="mt-4 rounded-sm border border-[#BFEADC] bg-[#EAFBF7] px-4 py-3">
              <p className="text-sm font-semibold text-[#146B4D]">
                {marcaLocal.tipo === "ENTRADA" ? "Ingreso capturado" : "Salida capturada"}
              </p>
              <p className="mt-1 text-xs text-[#146B4D]">
                {marcaLocal.hora.toLocaleTimeString("es-PE")} · {marcaLocal.latitud.toFixed(5)},{" "}
                {marcaLocal.longitud.toFixed(5)}
                {marcaLocal.precision ? ` · precisión ${Math.round(marcaLocal.precision)} m` : ""}
              </p>
            </div>
          )}

          {errorMarcacion && (
            <p className="mt-4 text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
              {errorMarcacion}
            </p>
          )}

          <div className="mt-5 grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => solicitarMarcacion("ENTRADA")}
              disabled={capturando !== null}
              className="h-12 rounded-sm bg-[#3150D4] text-white font-semibold disabled:opacity-60 flex items-center justify-center gap-2"
            >
              <Icono nombre="entrada" className="h-5 w-5" />
              {capturando === "ENTRADA" ? "Capturando" : "Ingreso"}
            </button>
            <button
              type="button"
              onClick={() => solicitarMarcacion("SALIDA")}
              disabled={capturando !== null}
              className="h-12 rounded-sm border border-[#DCE3EF] bg-white text-[#27324A] font-semibold disabled:opacity-60 flex items-center justify-center gap-2"
            >
              <Icono nombre="salida" className="h-5 w-5 text-primario" />
              {capturando === "SALIDA" ? "Capturando" : "Salida"}
            </button>
          </div>
        </section>

        <div className="flex items-center justify-between pt-2">
          <h2 className="text-base font-bold text-[#061229]">Mi agenda</h2>
          <span className="h-9 w-9 rounded-sm bg-white border border-[#DCE3EF] text-[#3150D4] flex items-center justify-center">
            <Icono nombre="agenda" className="h-5 w-5" />
          </span>
        </div>

        {cargando && <p className="text-sm text-textoSuave">Cargando agenda…</p>}

        {error && (
          <p className="text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
            {error}
          </p>
        )}

        {!cargando && !error && asignaciones.length === 0 && (
          <p className="text-sm text-textoSuave">No tienes asignaciones registradas.</p>
        )}

        {asignaciones.map((asignacion) => (
          <article
            key={asignacion.id}
            className="bg-superficie rounded-sm border border-[#DEE5F0] shadow-[0_10px_26px_rgba(20,30,70,0.06)] p-4 flex flex-col gap-2"
          >
            <div className="flex items-center justify-between gap-2">
              <h3 className="font-semibold text-[#061229]">{asignacion.nombreProyecto}</h3>
              <span
                className={`text-xs font-semibold px-2 py-1 rounded-full whitespace-nowrap ${
                  asignacion.estado === "VIGENTE"
                    ? "bg-exitoFondo text-exitoTexto"
                    : "bg-primario/10 text-primario"
                }`}
              >
                {asignacion.estado === "VIGENTE" ? "Vigente" : "Programada"}
              </span>
            </div>
            <p className="text-sm text-textoSuave">{asignacion.nombreUbicacion}</p>
            <p className="text-sm text-textoSuave">Turno: {asignacion.nombreHorario}</p>
            <p className="text-xs text-textoSuave">
              Desde {asignacion.fechaInicio}
              {asignacion.fechaFin ? ` hasta ${asignacion.fechaFin}` : " (sin fecha de término)"}
            </p>
          </article>
        ))}
      </main>
    </div>
  );
}
