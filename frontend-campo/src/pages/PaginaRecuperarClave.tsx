import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAutenticacion } from "../services/servicioAutenticacion";

/**
 * HU03: recuperación de acceso, solo para SUPERVISOR/RRHH_ADMIN (los roles con
 * contraseña — un técnico recupera su acceso re-enrolando su dispositivo, ver HU05).
 *
 * El backend todavía no envía correos reales (no hay SMTP configurado en el proyecto):
 * el enlace queda en el log del servidor. Por eso esta pantalla, además de aceptar
 * ?token=... en la URL (lo que haría un enlace de correo real), permite pegar el código
 * a mano — es un paso intermedio honesto mientras no exista el envío real.
 */
export function PaginaRecuperarClave() {
  const [parametros] = useSearchParams();
  const tokenDeUrl = parametros.get("token");

  const [etapa, setEtapa] = useState<"solicitar" | "restablecer">(tokenDeUrl ? "restablecer" : "solicitar");
  const [correo, setCorreo] = useState("");
  const [token, setToken] = useState(tokenDeUrl ?? "");
  const [nuevaPassword, setNuevaPassword] = useState("");
  const [confirmarPassword, setConfirmarPassword] = useState("");
  const [mensaje, setMensaje] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [restablecida, setRestablecida] = useState(false);

  async function manejarSolicitar(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const respuesta = await servicioAutenticacion.solicitarRecuperacion({ correo });
      setMensaje(respuesta.mensaje);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo conectar con el servidor");
    } finally {
      setEnviando(false);
    }
  }

  async function manejarRestablecer(evento: FormEvent) {
    evento.preventDefault();
    setError(null);

    if (nuevaPassword !== confirmarPassword) {
      setError("Las contraseñas no coinciden");
      return;
    }

    setEnviando(true);
    try {
      const respuesta = await servicioAutenticacion.restablecerClave({ token, nuevaPassword });
      setMensaje(respuesta.mensaje);
      setRestablecida(true);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo conectar con el servidor");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-fondo px-4">
      <div className="w-full max-w-sm flex flex-col items-center gap-6">
        <div className="flex flex-col items-center gap-2 text-center">
          <div className="w-14 h-14 rounded-md bg-gradient-to-br from-primario to-acento flex items-center justify-center text-white font-bold text-xl">
            CC
          </div>
          <h1 className="text-xl font-bold text-texto">Recuperar acceso</h1>
          <p className="text-sm text-textoSuave">Solo para cuentas de Supervisor o RRHH.</p>
        </div>

        {restablecida ? (
          <div className="w-full bg-superficie rounded-md shadow-tarjeta p-6 flex flex-col gap-4 text-center">
            <p className="text-sm text-exitoTexto bg-exitoFondo border border-exito/30 rounded-sm px-3 py-2">
              {mensaje}
            </p>
            <Link
              to="/login"
              className="h-12 flex items-center justify-center rounded-sm bg-primario text-white font-semibold hover:bg-primarioOscuro transition-colors"
            >
              Ir a iniciar sesión
            </Link>
          </div>
        ) : etapa === "solicitar" ? (
          <form onSubmit={manejarSolicitar} className="w-full bg-superficie rounded-md shadow-tarjeta p-6 flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <label htmlFor="correo" className="text-sm font-semibold text-texto">
                Correo corporativo
              </label>
              <input
                id="correo"
                type="email"
                required
                value={correo}
                onChange={(evento) => setCorreo(evento.target.value)}
                placeholder="nombre.apellido@empresa.com"
                className="h-12 px-3 rounded-sm border border-borde text-sm focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario"
              />
            </div>

            {mensaje && (
              <p className="text-sm text-exitoTexto bg-exitoFondo border border-exito/30 rounded-sm px-3 py-2">
                {mensaje}
              </p>
            )}
            {error && (
              <p className="text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={enviando}
              className="h-12 rounded-sm bg-primario text-white font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
            >
              {enviando ? "Enviando…" : "Enviar instrucciones"}
            </button>

            <button
              type="button"
              onClick={() => setEtapa("restablecer")}
              className="text-xs text-textoSuave hover:text-primario"
            >
              Ya tengo un código de recuperación
            </button>
          </form>
        ) : (
          <form onSubmit={manejarRestablecer} className="w-full bg-superficie rounded-md shadow-tarjeta p-6 flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <label htmlFor="token" className="text-sm font-semibold text-texto">
                Código de recuperación
              </label>
              <input
                id="token"
                type="text"
                required
                value={token}
                onChange={(evento) => setToken(evento.target.value)}
                placeholder="Pega aquí el código recibido"
                className="h-12 px-3 rounded-sm border border-borde text-sm focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario"
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="nuevaPassword" className="text-sm font-semibold text-texto">
                Nueva contraseña
              </label>
              <input
                id="nuevaPassword"
                type="password"
                required
                value={nuevaPassword}
                onChange={(evento) => setNuevaPassword(evento.target.value)}
                placeholder="Mínimo 8 caracteres, con letras y números"
                className="h-12 px-3 rounded-sm border border-borde text-sm focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario"
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="confirmarPassword" className="text-sm font-semibold text-texto">
                Confirmar contraseña
              </label>
              <input
                id="confirmarPassword"
                type="password"
                required
                value={confirmarPassword}
                onChange={(evento) => setConfirmarPassword(evento.target.value)}
                className="h-12 px-3 rounded-sm border border-borde text-sm focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario"
              />
            </div>

            {error && (
              <p className="text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={enviando}
              className="h-12 rounded-sm bg-primario text-white font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
            >
              {enviando ? "Guardando…" : "Restablecer contraseña"}
            </button>
          </form>
        )}

        <Link to="/login" className="text-xs text-textoSuave hover:text-primario">
          Volver a iniciar sesión
        </Link>
      </div>
    </div>
  );
}
