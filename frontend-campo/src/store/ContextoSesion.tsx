import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import type { RespuestaPerfil } from "../types/api";

const CLAVE_ALMACENAMIENTO = "clickclak.sesion";

interface SesionActiva {
  token: string;
  perfil: RespuestaPerfil;
}

interface ContextoSesionValor {
  token: string | null;
  perfil: RespuestaPerfil | null;
  cargando: boolean;
  iniciarSesion: (sesion: SesionActiva) => void;
  cerrarSesion: () => void;
}

const ContextoSesion = createContext<ContextoSesionValor | undefined>(undefined);

/** Persiste la sesión en localStorage: recargar la PWA no debe forzar un nuevo login. */
export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [perfil, setPerfil] = useState<RespuestaPerfil | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    const guardada = localStorage.getItem(CLAVE_ALMACENAMIENTO);
    if (guardada) {
      try {
        const sesion = JSON.parse(guardada) as SesionActiva;
        setToken(sesion.token);
        setPerfil(sesion.perfil);
      } catch {
        localStorage.removeItem(CLAVE_ALMACENAMIENTO);
      }
    }
    setCargando(false);
  }, []);

  function iniciarSesion(sesion: SesionActiva) {
    localStorage.setItem(CLAVE_ALMACENAMIENTO, JSON.stringify(sesion));
    setToken(sesion.token);
    setPerfil(sesion.perfil);
  }

  function cerrarSesion() {
    localStorage.removeItem(CLAVE_ALMACENAMIENTO);
    setToken(null);
    setPerfil(null);
  }

  return (
    <ContextoSesion.Provider value={{ token, perfil, cargando, iniciarSesion, cerrarSesion }}>
      {children}
    </ContextoSesion.Provider>
  );
}

export function useSesion(): ContextoSesionValor {
  const contexto = useContext(ContextoSesion);
  if (!contexto) {
    throw new Error("useSesion debe usarse dentro de <ProveedorSesion>");
  }
  return contexto;
}
