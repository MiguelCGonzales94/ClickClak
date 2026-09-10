import { Navigate, Outlet } from "react-router-dom";
import { useSesion } from "../store/ContextoSesion";

interface Props {
  /** Si se omite, cualquier usuario autenticado puede entrar. */
  rolesPermitidos?: string[];
}

/** Redirige a /login si no hay sesión activa; evita el parpadeo mientras se lee localStorage. */
export function RutaProtegida({ rolesPermitidos }: Props) {
  const { token, perfil, cargando } = useSesion();

  if (cargando) {
    return null;
  }
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  if (rolesPermitidos && (!perfil || !rolesPermitidos.includes(perfil.rol))) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
