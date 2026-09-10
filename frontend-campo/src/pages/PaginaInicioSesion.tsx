import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { servicioWebAuthn, soportaWebAuthn } from "../services/servicioWebAuthn";
import { useSesion } from "../store/ContextoSesion";

export function PaginaInicioSesion() {
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [autenticandoBiometria, setAutenticandoBiometria] = useState(false);
  const { iniciarSesion } = useSesion();
  const navegar = useNavigate();

  const compatibleConWebAuthn = soportaWebAuthn();

  async function manejarEnvio(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const respuestaLogin = await servicioAutenticacion.iniciarSesion({ correo, password });
      const perfil = await servicioAutenticacion.obtenerPerfil(respuestaLogin.token);
      iniciarSesion({ token: respuestaLogin.token, perfil });
      navegar("/agenda", { replace: true });
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo conectar con el servidor");
    } finally {
      setEnviando(false);
    }
  }

  async function manejarBiometria() {
    setError(null);
    if (!correo) {
      setError("Ingresa tu correo corporativo para continuar con huella o rostro");
      return;
    }
    setAutenticandoBiometria(true);
    try {
      const respuestaLogin = await servicioWebAuthn.autenticar(correo);
      const perfil = await servicioAutenticacion.obtenerPerfil(respuestaLogin.token);
      iniciarSesion({ token: respuestaLogin.token, perfil });
      navegar("/agenda", { replace: true });
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo completar la autenticación biométrica");
    } finally {
      setAutenticandoBiometria(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-fondo px-4">
      <div className="w-full max-w-sm flex flex-col items-center gap-8">
        <div className="flex flex-col items-center gap-2 text-center">
          <div className="w-14 h-14 rounded-md bg-gradient-to-br from-primario to-acento flex items-center justify-center text-white font-bold text-xl">
            CC
          </div>
          <h1 className="text-2xl font-bold text-texto">ClickClak</h1>
          <p className="text-sm text-textoSuave">Control de asistencia para personal de campo</p>
        </div>

        <form
          onSubmit={manejarEnvio}
          className="w-full bg-superficie rounded-md shadow-tarjeta p-6 flex flex-col gap-4"
        >
          <div className="flex flex-col gap-1">
            <label htmlFor="correo" className="text-sm font-semibold text-texto">
              Correo corporativo
            </label>
            <input
              id="correo"
              type="email"
              required
              autoComplete="username"
              value={correo}
              onChange={(evento) => setCorreo(evento.target.value)}
              placeholder="nombre.apellido@empresa.com"
              className="h-12 px-3 rounded-sm border border-borde text-sm focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="password" className="text-sm font-semibold text-texto">
              Contraseña
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(evento) => setPassword(evento.target.value)}
              placeholder="••••••••"
              className="h-12 px-3 rounded-sm border border-borde text-sm focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario"
            />
            <Link to="/recuperar-clave" className="self-end text-xs text-primario hover:text-primarioOscuro">
              ¿Olvidaste tu contraseña?
            </Link>
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
            {enviando ? "Ingresando…" : "Iniciar sesión"}
          </button>

          <div className="flex items-center gap-3 text-xs text-textoSuave">
            <span className="flex-1 h-px bg-borde" />
            o
            <span className="flex-1 h-px bg-borde" />
          </div>

          <button
            type="button"
            onClick={manejarBiometria}
            disabled={!compatibleConWebAuthn || autenticandoBiometria}
            className="h-12 rounded-sm border border-borde text-texto font-semibold hover:border-primario disabled:opacity-60 transition-colors"
          >
            {autenticandoBiometria ? "Verificando…" : "Ingresar con huella o rostro"}
          </button>
          {!compatibleConWebAuthn && (
            <p className="text-xs text-textoSuave text-center">
              Este navegador no admite acceso biométrico. Usa un Android con Chrome actualizado, o inicia sesión con tu contraseña.
            </p>
          )}
        </form>

        <Link to="/registrar-dispositivo" className="text-xs text-textoSuave hover:text-primario text-center">
          ¿Eres supervisor o RRHH? Registrar un dispositivo
        </Link>
      </div>
    </div>
  );
}
