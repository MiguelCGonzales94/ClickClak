import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { useSesion } from "../store/ContextoSesion";

const ENLACES_NAV = [
  { a: "/", etiqueta: "Dashboard", icono: "📊", fin: true },
  { a: "/usuarios", etiqueta: "Usuarios", icono: "👤" },
  { a: "/sedes", etiqueta: "Sedes y servicios", icono: "📍" },
  { a: "/turnos", etiqueta: "Turnos", icono: "⏰" },
  { a: "/asignaciones", etiqueta: "Asignaciones", icono: "🗂️" },
];

/** Shell del panel: sidebar oscuro fijo + contenido claro, estilo pedido por el usuario. */
export function EstructuraPanel() {
  const { perfil, token, cerrarSesion } = useSesion();
  const navegar = useNavigate();

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
    <div className="min-h-screen flex bg-fondo">
      <aside className="w-64 shrink-0 bg-panelOscuro flex flex-col">
        <div className="h-16 flex items-center gap-3 px-5 border-b border-panelOscuroBorde">
          <div className="w-8 h-8 rounded-md bg-gradient-to-br from-primario to-acento flex items-center justify-center text-white font-bold text-sm">
            CC
          </div>
          <span className="text-textoClaro font-bold text-lg">ClickClak</span>
        </div>

        <nav className="flex-1 px-3 py-4 flex flex-col gap-1">
          <p className="px-3 text-[11px] font-semibold tracking-wide text-textoClaroSuave uppercase mb-1">
            Administración
          </p>
          {ENLACES_NAV.map((enlace) => (
            <NavLink
              key={enlace.a}
              to={enlace.a}
              end={enlace.fin}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-sm text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-primario text-white"
                    : "text-textoClaroSuave hover:bg-panelOscuroHover hover:text-textoClaro"
                }`
              }
            >
              <span aria-hidden="true">{enlace.icono}</span>
              {enlace.etiqueta}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-panelOscuroBorde p-3 flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-panelOscuroHover flex items-center justify-center text-textoClaro text-sm font-semibold shrink-0">
            {perfil?.nombres?.charAt(0)}
            {perfil?.apellidos?.charAt(0)}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm text-textoClaro font-semibold truncate">
              {perfil?.nombres} {perfil?.apellidos}
            </p>
            <p className="text-xs text-textoClaroSuave truncate">{perfil?.rol}</p>
          </div>
          <button
            onClick={manejarCerrarSesion}
            title="Cerrar sesión"
            aria-label="Cerrar sesión"
            className="text-textoClaroSuave hover:text-peligro text-lg leading-none px-1"
          >
            ⎋
          </button>
        </div>
      </aside>

      <div className="flex-1 min-w-0 flex flex-col">
        <Outlet />
      </div>
    </div>
  );
}
