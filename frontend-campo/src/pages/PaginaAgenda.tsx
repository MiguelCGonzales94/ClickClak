import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAgenda } from "../services/servicioAgenda";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaAsignacion } from "../types/api";

/** HU09: agenda propia del colaborador — sedes, turnos y estado de sus asignaciones. */
export function PaginaAgenda() {
  const { token, perfil, cerrarSesion } = useSesion();
  const navegar = useNavigate();
  const [asignaciones, setAsignaciones] = useState<RespuestaAsignacion[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <div className="min-h-screen bg-fondo">
      <header className="bg-superficie border-b border-borde px-4 py-4 flex items-center justify-between">
        <div>
          <p className="text-sm text-textoSuave">Hola,</p>
          <p className="font-semibold text-texto">
            {perfil?.nombres} {perfil?.apellidos}
          </p>
        </div>
        <button
          onClick={manejarCerrarSesion}
          className="text-sm font-semibold text-primario hover:text-primarioOscuro"
        >
          Cerrar sesión
        </button>
      </header>

      <main className="max-w-lg mx-auto px-4 py-6 flex flex-col gap-4">
        <h2 className="text-lg font-bold text-texto">Mi agenda</h2>

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
            className="bg-superficie rounded-md shadow-tarjeta p-4 flex flex-col gap-2"
          >
            <div className="flex items-center justify-between gap-2">
              <h3 className="font-semibold text-texto">{asignacion.nombreProyecto}</h3>
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
