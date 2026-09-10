import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Icono } from "../components/Iconos";
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
    <div className="min-h-screen bg-[#F3F5F9] px-4 py-6 flex justify-center">
      <div className="w-full max-w-[420px] min-h-[calc(100vh-48px)] flex flex-col">
        <header className="flex items-center justify-between pb-6">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-sm bg-primario flex items-center justify-center text-white font-bold">
              C
            </div>
            <div>
              <h1 className="text-lg font-bold text-[#061229]">ClickClak</h1>
              <p className="text-xs text-[#66718A]">App de campo</p>
            </div>
          </div>
          <span className="h-9 w-9 rounded-sm border border-[#DCE3EF] bg-white text-[#3150D4] flex items-center justify-center">
            <Icono nombre="seguridad" className="h-5 w-5" />
          </span>
        </header>

        <section className="rounded-sm bg-[#10192C] text-white border border-white/10 p-5 mb-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Inicio de jornada</p>
          <h2 className="mt-3 text-2xl font-bold leading-tight">Accede y registra tu asistencia en campo</h2>
          <p className="mt-3 text-sm leading-6 text-slate-300">
            Usa tu correo corporativo y confirma tu identidad con el dispositivo autorizado.
          </p>
        </section>

        <form
          onSubmit={manejarEnvio}
          className="w-full bg-superficie rounded-sm border border-[#DEE5F0] shadow-[0_14px_34px_rgba(20,30,70,0.08)] p-5 flex flex-col gap-4"
        >
          <div className="flex flex-col gap-1">
            <label htmlFor="correo" className="text-sm font-semibold text-[#27324A]">
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
              className="h-12 px-3 rounded-sm border border-[#DCE3EF] bg-[#F8FAFD] text-sm focus:outline-none focus:ring-2 focus:ring-primario/20 focus:border-primario"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="password" className="text-sm font-semibold text-[#27324A]">
              Contraseña
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(evento) => setPassword(evento.target.value)}
              className="h-12 px-3 rounded-sm border border-[#DCE3EF] bg-[#F8FAFD] text-sm focus:outline-none focus:ring-2 focus:ring-primario/20 focus:border-primario"
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
            className="h-12 rounded-sm bg-[#3150D4] text-white font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
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
            className="h-12 rounded-sm border border-[#DCE3EF] text-[#27324A] font-semibold hover:border-primario disabled:opacity-60 transition-colors flex items-center justify-center gap-2"
          >
            <Icono nombre="biometria" className="h-5 w-5 text-primario" />
            {autenticandoBiometria ? "Verificando…" : "Ingresar con huella o rostro"}
          </button>
          {!compatibleConWebAuthn && (
            <p className="text-xs text-textoSuave text-center">
              Este navegador no admite acceso biométrico. Usa un Android con Chrome actualizado, o inicia sesión con tu contraseña.
            </p>
          )}
        </form>

        <Link to="/registrar-dispositivo" className="mt-5 text-xs text-textoSuave hover:text-primario text-center">
          ¿Eres supervisor o RRHH? Registrar un dispositivo
        </Link>
      </div>
    </div>
  );
}
