import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useSesion } from "../store/ContextoSesion";

interface Props {
  /** Si se omite, cualquier usuario autenticado puede entrar. */
  rolesPermitidos?: string[];
  /** Adónde ir cuando el rol no alcanza. Por defecto el login; una sección interna manda al inicio. */
  redirigirSinPermisoA?: string;
}

export const RUTA_CAMBIAR_CLAVE = "/cambiar-clave";

/**
 * Redirige a /login si no hay sesión activa; evita el parpadeo mientras se lee localStorage.
 * Con una clave temporal pendiente (HU04) la única pantalla disponible es el cambio de clave.
 */
export function RutaProtegida({ rolesPermitidos, redirigirSinPermisoA = "/login" }: Props) {
  const { token, perfil, cargando } = useSesion();
  const ubicacion = useLocation();

  if (cargando) {
    return null;
  }
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  if (perfil?.debeCambiarClave && ubicacion.pathname !== RUTA_CAMBIAR_CLAVE) {
    return <Navigate to={RUTA_CAMBIAR_CLAVE} replace />;
  }
  if (rolesPermitidos && (!perfil || !rolesPermitidos.includes(perfil.rol))) {
    return <Navigate to={redirigirSinPermisoA} replace />;
  }
  return <Outlet />;
}
