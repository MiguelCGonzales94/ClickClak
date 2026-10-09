import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Campo } from "../components/Campo";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { useSesion } from "../store/ContextoSesion";
import { validarClaveNueva } from "../usuarios/reglas";

/**
 * HU04: cambio de la propia contraseña. Es obligatorio (y la única pantalla disponible) cuando un
 * administrador restableció la clave; si no, se llega desde el menú. Al cambiarla el servidor
 * revoca el token en uso, así que se vuelve a iniciar sesión con la nueva.
 */
export function PaginaCambiarClave() {
  const { token, perfil, cerrarSesion } = useSesion();
  const navegar = useNavigate();
  const [claveActual, setClaveActual] = useState("");
  const [claveNueva, setClaveNueva] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const obligatorio = perfil?.debeCambiarClave === true;

  async function manejarEnvio(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;
    const problema = validarClaveNueva(claveActual, claveNueva, confirmacion);
    setError(problema);
    if (problema) return;

    setEnviando(true);
    try {
      await servicioAutenticacion.cambiarClave(token, { claveActual, claveNueva });
      cerrarSesion();
      navegar("/login", { replace: true, state: { mensaje: "Contraseña actualizada. Inicie sesión con la nueva." } });
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo cambiar la contraseña");
    } finally {
      setEnviando(false);
    }
  }

  async function salir() {
    if (token) {
      try {
        await servicioAutenticacion.cerrarSesion(token);
      } catch {
        // El cierre local debe funcionar igual sin conexión.
      }
    }
    cerrarSesion();
    navegar("/login", { replace: true });
  }

  return (
    <div className="min-h-screen bg-fondo flex items-center justify-center px-4 py-10">
      <form onSubmit={manejarEnvio} className="w-full max-w-md rounded-md bg-superficie p-7 shadow-tarjeta">
        <h1 className="text-xl font-bold text-texto">Cambiar contraseña</h1>
        <p className="mt-1 text-sm text-textoSuave">
          {obligatorio
            ? "Un administrador restableció su contraseña. Elija una nueva para continuar."
            : "Por seguridad, al cambiarla tendrá que iniciar sesión de nuevo."}
        </p>

        <div className="mt-5 grid gap-4">
          <Campo etiqueta={obligatorio ? "Contraseña temporal" : "Contraseña actual"}>
            <input
              required
              type="password"
              autoComplete="current-password"
              maxLength={72}
              value={claveActual}
              onChange={(e) => setClaveActual(e.target.value)}
              className="campo"
            />
          </Campo>
          <Campo etiqueta="Contraseña nueva">
            <input
              required
              type="password"
              autoComplete="new-password"
              maxLength={72}
              value={claveNueva}
              onChange={(e) => setClaveNueva(e.target.value)}
              placeholder="Mínimo 8 caracteres, letras y números"
              className="campo"
            />
          </Campo>
          <Campo etiqueta="Confirmar contraseña nueva">
            <input
              required
              type="password"
              autoComplete="new-password"
              maxLength={72}
              value={confirmacion}
              onChange={(e) => setConfirmacion(e.target.value)}
              className="campo"
            />
          </Campo>
        </div>

        {error && (
          <p role="alert" className="mt-4 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={enviando}
          className="mt-5 h-11 w-full rounded-sm bg-primario text-sm font-semibold text-white transition-colors hover:bg-primarioOscuro disabled:opacity-60"
        >
          {enviando ? "Guardando…" : "Cambiar contraseña"}
        </button>

        <div className="mt-4 text-center text-sm">
          {obligatorio ? (
            <button type="button" onClick={salir} className="font-semibold text-primario hover:text-primarioOscuro">
              Cerrar sesión
            </button>
          ) : (
            <Link to="/" className="font-semibold text-primario hover:text-primarioOscuro">
              Volver al panel
            </Link>
          )}
        </div>
      </form>
    </div>
  );
}
