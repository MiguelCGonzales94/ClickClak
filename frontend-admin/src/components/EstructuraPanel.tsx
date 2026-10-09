import { useMemo, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { Icono, type NombreIcono } from "./Iconos";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { useSesion } from "../store/ContextoSesion";

const ENLACES_NAV: { a: string; etiqueta: string; icono: NombreIcono; fin?: boolean; soloRol?: string }[] = [
  { a: "/", etiqueta: "Dashboard", icono: "dashboard", fin: true },
  // La gestión de usuarios (alta, claves, bajas) es exclusiva de RRHH_ADMIN en el servidor.
  { a: "/usuarios", etiqueta: "Usuarios", icono: "usuarios", soloRol: "RRHH_ADMIN" },
  { a: "/asistencia", etiqueta: "Asistencia", icono: "agenda" },
  { a: "/sedes", etiqueta: "Sedes y servicios", icono: "edificio" },
  { a: "/turnos", etiqueta: "Turnos", icono: "turnos" },
  { a: "/asignaciones", etiqueta: "Asignaciones", icono: "asignaciones" },
  { a: "/incidencias", etiqueta: "Incidencias", icono: "incidencias" },
];

/** Shell del panel: sidebar oscuro fijo + contenido claro, estilo pedido por el usuario. */
export function EstructuraPanel() {
  const { perfil, token, cerrarSesion } = useSesion();
  const navegar = useNavigate();
  const [busqueda, setBusqueda] = useState("");

  const enlacesVisibles = useMemo(() => {
    const texto = busqueda.trim().toLowerCase();
    const permitidos = ENLACES_NAV.filter((enlace) => !enlace.soloRol || enlace.soloRol === perfil?.rol);
    if (!texto) return permitidos;
    return permitidos.filter((enlace) => enlace.etiqueta.toLowerCase().includes(texto));
  }, [busqueda, perfil?.rol]);

  async function manejarCerrarSesion() {
    if (token) {
      try {
        await servicioAutenticacion.cerrarSesion(token);
      } catch {
        // Se ignora deliberadamente: el cierre local debe funcionar igual sin conexión.
      }
    }
    cerrarSesion();
    navegar("/login", { replace: true });
  }

  return (
    <div className="min-h-screen flex bg-fondo text-texto">
      <aside className="w-60 shrink-0 bg-[#10192C] text-textoClaro flex flex-col shadow-[8px_0_28px_rgba(10,16,32,0.18)]">
        <div className="h-16 flex items-center gap-3 px-3 border-b border-white/10">
          <div className="w-9 h-9 rounded-sm bg-primario flex items-center justify-center text-white font-bold text-sm tracking-wide">
            C
          </div>
          <span className="text-white font-bold text-lg flex-1">ClickClak</span>
          <span className="h-8 w-8 rounded-sm text-slate-400 flex items-center justify-center">
            <Icono nombre="chevronIzquierda" className="mx-auto h-4 w-4" />
          </span>
        </div>

        <div className="px-3 pt-4">
          <label className="h-10 rounded-sm bg-white/8 border border-white/8 px-3 flex items-center gap-2 text-sm text-slate-400 focus-within:border-primario/60 focus-within:bg-white/10 transition-colors">
            <Icono nombre="buscar" className="h-4 w-4 shrink-0" />
            <input
              type="search"
              placeholder="Buscar..."
              value={busqueda}
              onChange={(evento) => setBusqueda(evento.target.value)}
              className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-slate-500 text-slate-200"
            />
            <span className="rounded-[5px] border border-slate-600 px-1.5 py-0.5 text-[11px] leading-none text-slate-400">
              Ctrl+K
            </span>
          </label>
        </div>

        <nav className="flex-1 px-2 py-6 flex flex-col gap-1">
          <p className="px-3 pb-2 text-[11px] font-semibold tracking-wide text-slate-400 uppercase">
            Administración
          </p>
          {enlacesVisibles.map((enlace) => (
            <NavLink
              key={enlace.a}
              to={enlace.a}
              end={enlace.fin}
              className={({ isActive }) =>
                `group flex items-center gap-3 px-3 py-2.5 rounded-sm text-sm font-semibold transition-colors ${
                  isActive
                    ? "bg-[#3150D4] text-white shadow-[0_10px_22px_rgba(31,80,216,0.22)]"
                    : "text-slate-300 hover:bg-white/8 hover:text-white"
                }`
              }
            >
              <Icono nombre={enlace.icono} className="h-5 w-5 shrink-0 opacity-90" />
              {enlace.etiqueta}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-white/10 p-3 flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-primario flex items-center justify-center text-white text-sm font-bold shrink-0">
            {perfil?.nombres?.charAt(0)}
            {perfil?.apellidos?.charAt(0)}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm text-white font-semibold truncate">
              {perfil?.nombres} {perfil?.apellidos}
            </p>
            <p className="text-xs text-slate-400 truncate">{perfil?.rol}</p>
          </div>
          <Link
            to="/cambiar-clave"
            title="Cambiar mi contraseña"
            aria-label="Cambiar mi contraseña"
            className="h-8 w-8 rounded-sm text-slate-400 hover:bg-white/10 hover:text-white transition-colors flex items-center justify-center"
          >
            <Icono nombre="seguridad" className="h-5 w-5" />
          </Link>
          <button
            onClick={manejarCerrarSesion}
            title="Cerrar sesión"
            aria-label="Cerrar sesión"
            className="h-8 w-8 rounded-sm text-slate-400 hover:bg-white/10 hover:text-white transition-colors"
          >
            <Icono nombre="logout" className="mx-auto h-5 w-5" />
          </button>
        </div>
      </aside>

      <div className="flex-1 min-w-0 flex flex-col">
        <Outlet />
      </div>
    </div>
  );
}
