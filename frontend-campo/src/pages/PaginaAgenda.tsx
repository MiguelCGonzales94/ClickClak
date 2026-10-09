import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Icono } from "../components/Iconos";
import { ErrorHttp } from "../services/clienteApi";
import {
  encolarMarcacion,
  esErrorReintentable,
  listarMarcacionesOffline,
  mensajeDeError,
  sincronizarMarcacionesPendientes,
} from "../offline/colaMarcaciones";
import type { MarcacionOffline } from "../offline/baseDatos";
import { servicioAgenda } from "../services/servicioAgenda";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { servicioDispositivos } from "../services/servicioDispositivos";
import { servicioMarcaciones } from "../services/servicioMarcaciones";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaAsignacion, RespuestaDispositivo, RespuestaMarcacion, TipoEvento } from "../types/api";

type TipoMarca = Extract<TipoEvento, "ENTRADA" | "SALIDA">;

const etiquetaEstadoMarcacion: Record<RespuestaMarcacion["estadoValidacion"], string> = {
  VALIDO: "Válida",
  OBSERVADO: "Observada",
  FUERA_DE_TOLERANCIA: "Fuera de tolerancia",
  SOSPECHOSO: "Revisar",
  SIN_ASIGNACION: "Sin asignación",
};

const etiquetaEstadoOffline: Record<MarcacionOffline["estado"], string> = {
  PENDIENTE: "Pendiente",
  SINCRONIZANDO: "Sincronizando",
  SINCRONIZADA: "Sincronizada",
  ERROR: "Error",
};

function crearUuidCliente(): string {
  if ("randomUUID" in crypto) {
    return crypto.randomUUID();
  }

  return "10000000-1000-4000-8000-100000000000".replace(/[018]/g, (caracter) =>
    (Number(caracter) ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (Number(caracter) / 4)))).toString(16),
  );
}

/** HU09: agenda propia del colaborador — sedes, turnos y estado de sus asignaciones. */
export function PaginaAgenda() {
  const { token, perfil, cerrarSesion } = useSesion();
  const usuarioId = perfil?.id;
  const navegar = useNavigate();
  const [asignaciones, setAsignaciones] = useState<RespuestaAsignacion[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloj, setReloj] = useState(new Date());
  const [capturando, setCapturando] = useState<TipoMarca | null>(null);
  const [dispositivos, setDispositivos] = useState<RespuestaDispositivo[]>([]);
  const [resultadoMarcacion, setResultadoMarcacion] = useState<RespuestaMarcacion | null>(null);
  const [marcacionesOffline, setMarcacionesOffline] = useState<MarcacionOffline[]>([]);
  const [sincronizandoCola, setSincronizandoCola] = useState(false);
  const [avisoOffline, setAvisoOffline] = useState<string | null>(null);
  const [errorMarcacion, setErrorMarcacion] = useState<string | null>(null);
  const sincronizandoRef = useRef(false);

  const refrescarCola = useCallback(async () => {
    if (!usuarioId) return;
    setMarcacionesOffline(await listarMarcacionesOffline(usuarioId));
  }, [usuarioId]);

  useEffect(() => {
    if (!token || !usuarioId) return;
    setCargando(true);
    Promise.all([
      servicioAgenda.obtenerMiAgenda(token),
      servicioDispositivos.listarMios(token),
      listarMarcacionesOffline(usuarioId),
    ])
      .then(([agenda, dispositivosActivos, marcacionesLocales]) => {
        setAsignaciones(agenda);
        setDispositivos(dispositivosActivos);
        setMarcacionesOffline(marcacionesLocales);
      })
      .catch((err) => {
        setError(err instanceof ErrorHttp ? err.message : "No se pudo cargar la información inicial");
      })
      .finally(() => setCargando(false));
  }, [token, usuarioId]);

  const sincronizarCola = useCallback(async () => {
    if (!token || !usuarioId || sincronizandoRef.current) return;

    sincronizandoRef.current = true;
    setSincronizandoCola(true);
    setAvisoOffline(null);

    try {
      const resumen = await sincronizarMarcacionesPendientes(token, usuarioId);
      await refrescarCola();
      if (resumen.sincronizadas > 0) {
        setAvisoOffline(
          `${resumen.sincronizadas} marcación${resumen.sincronizadas === 1 ? "" : "es"} sincronizada${
            resumen.sincronizadas === 1 ? "" : "s"
          }.`,
        );
      }
    } finally {
      sincronizandoRef.current = false;
      setSincronizandoCola(false);
    }
  }, [refrescarCola, token, usuarioId]);

  useEffect(() => {
    const intervalo = window.setInterval(() => setReloj(new Date()), 1000);
    return () => window.clearInterval(intervalo);
  }, []);

  useEffect(() => {
    if (!token || !usuarioId) return;
    const manejarOnline = () => {
      void sincronizarCola();
    };

    window.addEventListener("online", manejarOnline);
    if (navigator.onLine) {
      void sincronizarCola();
    }

    return () => window.removeEventListener("online", manejarOnline);
  }, [sincronizarCola, token, usuarioId]);

  const asignacionPrincipal = useMemo(
    () => asignaciones.find((asignacion) => asignacion.estado === "VIGENTE") ?? asignaciones[0],
    [asignaciones],
  );
  // Un técnico puede tener varias sedes vigentes a la vez: la tarjeta las lista todas.
  const asignacionesVigentes = useMemo(
    () => asignaciones.filter((asignacion) => asignacion.estado === "VIGENTE"),
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

  async function guardarMarcacionOffline(
    tipo: TipoMarca,
    solicitud: Parameters<typeof encolarMarcacion>[1],
    error?: unknown,
  ) {
    if (!usuarioId) return;
    await encolarMarcacion(usuarioId, solicitud, error);
    await refrescarCola();
    setResultadoMarcacion(null);
    setAvisoOffline(
      `${tipo === "ENTRADA" ? "Ingreso" : "Salida"} guardado sin conexión. Se sincronizará al recuperar señal.`,
    );
  }

  function solicitarMarcacion(tipo: TipoMarca) {
    setErrorMarcacion(null);
    setAvisoOffline(null);
    setResultadoMarcacion(null);

    if (!token || !usuarioId) {
      setErrorMarcacion("La sesión no está disponible. Vuelve a iniciar sesión.");
      return;
    }

    const dispositivo = dispositivos[0];
    if (!dispositivo) {
      setErrorMarcacion("No hay un dispositivo activo asociado a tu cuenta.");
      return;
    }

    if (!("geolocation" in navigator)) {
      setErrorMarcacion("Este dispositivo no permite capturar ubicación desde el navegador.");
      return;
    }

    setCapturando(tipo);
    navigator.geolocation.getCurrentPosition(
      async (posicion) => {
        const solicitud = {
          uuidCliente: crearUuidCliente(),
          usuarioId,
          dispositivoId: dispositivo.id,
          tipoEvento: tipo,
          horaEvento: new Date().toISOString(),
          latitud: posicion.coords.latitude,
          longitud: posicion.coords.longitude,
          precisionMetros: posicion.coords.accuracy,
        };

        try {
          if (!navigator.onLine) {
            await guardarMarcacionOffline(tipo, solicitud);
            return;
          }

          const marcacion = await servicioMarcaciones.registrar(token, solicitud);
          setResultadoMarcacion(marcacion);
          await refrescarCola();
        } catch (err) {
          if (esErrorReintentable(err)) {
            await guardarMarcacionOffline(tipo, solicitud, err);
            return;
          }
          setErrorMarcacion(mensajeDeError(err));
        } finally {
          setCapturando(null);
        }
      },
      () => {
        setErrorMarcacion("No se pudo obtener la ubicación. Revisa el permiso del navegador.");
        setCapturando(null);
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 },
    );
  }

  const dispositivoPrincipal = dispositivos[0];

  const detalleDispositivo = dispositivoPrincipal
    ? dispositivoPrincipal.nombreDispositivo ?? "Dispositivo autorizado"
    : "Sin dispositivo autorizado";

  const detalleMarcacion = resultadoMarcacion
    ? `${new Date(resultadoMarcacion.horaEvento).toLocaleTimeString("es-PE")} · ${resultadoMarcacion.latitud.toFixed(5)}, ${resultadoMarcacion.longitud.toFixed(5)}${
        resultadoMarcacion.precisionMetros
          ? ` · precisión ${Math.round(resultadoMarcacion.precisionMetros)} m`
          : ""
      }`
    : null;

  const estadoMarcacion = resultadoMarcacion
    ? etiquetaEstadoMarcacion[resultadoMarcacion.estadoValidacion]
    : null;

  const distanciaMarcacion =
    resultadoMarcacion?.distanciaMetros !== null && resultadoMarcacion?.distanciaMetros !== undefined
      ? `${Math.round(resultadoMarcacion.distanciaMetros)} m de la sede`
      : null;

  const pendientesOffline = marcacionesOffline.filter(
    (marcacion) => marcacion.estado === "PENDIENTE" || marcacion.estado === "ERROR",
  ).length;

  const ultimasMarcacionesOffline = marcacionesOffline.slice(0, 3);

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
                {asignacionesVigentes.length > 1 ? (
                  <>
                    <p className="text-sm font-semibold text-[#061229]">{asignacionesVigentes.length} sedes asignadas hoy</p>
                    <ul className="mt-1 space-y-0.5 text-sm text-[#66718A]">
                      {asignacionesVigentes.map((asignacion) => (
                        <li key={asignacion.id}>
                          {asignacion.nombreUbicacion} · {asignacion.nombreHorario}
                        </li>
                      ))}
                    </ul>
                  </>
                ) : (
                  <>
                    <p className="text-sm font-semibold text-[#061229]">
                      {asignacionPrincipal?.nombreProyecto ?? "Sin asignación vigente"}
                    </p>
                    <p className="mt-1 text-sm text-[#66718A]">
                      {asignacionPrincipal
                        ? `${asignacionPrincipal.nombreUbicacion} · ${asignacionPrincipal.nombreHorario}`
                        : "La agenda se actualizará cuando el supervisor asigne una sede."}
                    </p>
                  </>
                )}
                <p className="mt-2 text-xs text-[#66718A]">{detalleDispositivo}</p>
              </div>
            </div>
          </div>

          {resultadoMarcacion && detalleMarcacion && estadoMarcacion && (
            <div className="mt-4 rounded-sm border border-[#BFEADC] bg-[#EAFBF7] px-4 py-3">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-semibold text-[#146B4D]">
                  {resultadoMarcacion.tipoEvento === "ENTRADA" ? "Ingreso registrado" : "Salida registrada"}
                </p>
                <span className="rounded-sm bg-white/80 px-2 py-1 text-xs font-bold text-[#146B4D]">
                  {estadoMarcacion}
                </span>
              </div>
              <p className="mt-1 text-xs text-[#146B4D]">{detalleMarcacion}</p>
              {distanciaMarcacion && <p className="mt-1 text-xs text-[#146B4D]">{distanciaMarcacion}</p>}
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
              disabled={capturando !== null || cargando}
              className="h-12 rounded-sm bg-[#3150D4] text-white font-semibold disabled:opacity-60 flex items-center justify-center gap-2"
            >
              <Icono nombre="entrada" className="h-5 w-5" />
              {capturando === "ENTRADA" ? "Registrando" : "Ingreso"}
            </button>
            <button
              type="button"
              onClick={() => solicitarMarcacion("SALIDA")}
              disabled={capturando !== null || cargando}
              className="h-12 rounded-sm border border-[#DCE3EF] bg-white text-[#27324A] font-semibold disabled:opacity-60 flex items-center justify-center gap-2"
            >
              <Icono nombre="salida" className="h-5 w-5 text-primario" />
              {capturando === "SALIDA" ? "Registrando" : "Salida"}
            </button>
          </div>

          {(avisoOffline || marcacionesOffline.length > 0) && (
            <div className="mt-4 rounded-sm border border-[#DCE3EF] bg-[#F8FAFD] px-4 py-3">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-[#061229]">
                    {pendientesOffline > 0
                      ? `${pendientesOffline} pendiente${pendientesOffline === 1 ? "" : "s"} por sincronizar`
                      : "Marcaciones al día"}
                  </p>
                  {avisoOffline && <p className="mt-1 text-xs text-[#66718A]">{avisoOffline}</p>}
                </div>
                {pendientesOffline > 0 && (
                  <button
                    type="button"
                    onClick={() => void sincronizarCola()}
                    disabled={sincronizandoCola || !navigator.onLine}
                    className="h-9 rounded-sm border border-[#C9D4E6] bg-white px-3 text-xs font-bold text-[#3150D4] disabled:opacity-50"
                  >
                    {sincronizandoCola ? "Sincronizando" : "Sincronizar"}
                  </button>
                )}
              </div>

              {ultimasMarcacionesOffline.length > 0 && (
                <div className="mt-3 divide-y divide-[#E5EAF2]">
                  {ultimasMarcacionesOffline.map((marcacion) => (
                    <div key={marcacion.uuidCliente} className="flex items-center justify-between gap-3 py-2">
                      <div className="min-w-0">
                        <p className="text-xs font-semibold text-[#27324A]">
                          {marcacion.solicitud.tipoEvento === "ENTRADA" ? "Ingreso" : "Salida"} ·{" "}
                          {new Date(marcacion.solicitud.horaEvento).toLocaleTimeString("es-PE")}
                        </p>
                        {marcacion.ultimoError && marcacion.estado === "ERROR" && (
                          <p className="mt-1 truncate text-xs text-peligroTexto">{marcacion.ultimoError}</p>
                        )}
                      </div>
                      <span
                        className={`shrink-0 rounded-sm px-2 py-1 text-xs font-bold ${
                          marcacion.estado === "SINCRONIZADA"
                            ? "bg-exitoFondo text-exitoTexto"
                            : marcacion.estado === "ERROR"
                              ? "bg-peligroFondo text-peligroTexto"
                              : "bg-[#EEF3FF] text-[#3150D4]"
                        }`}
                      >
                        {etiquetaEstadoOffline[marcacion.estado]}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
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
