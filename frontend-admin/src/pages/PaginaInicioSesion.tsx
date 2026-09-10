import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { ErrorHttp } from "../services/clienteApi";
import { servicioAutenticacion } from "../services/servicioAutenticacion";
import { useSesion } from "../store/ContextoSesion";

/** HU02: login de Supervisor/RRHH_ADMIN. Panel dividido negro/azul con huella, a pedido del usuario. */
export function PaginaInicioSesion() {
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const { iniciarSesion } = useSesion();
  const navegar = useNavigate();

  async function manejarEnvio(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const respuestaLogin = await servicioAutenticacion.iniciarSesion({ correo, password });
      const perfil = await servicioAutenticacion.obtenerPerfil(respuestaLogin.token);
      iniciarSesion({ token: respuestaLogin.token, perfil });
      navegar("/", { replace: true });
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo conectar con el servidor");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="min-h-screen bg-black">
      <div className="flex min-h-screen w-full overflow-hidden bg-black">
        <main className="relative flex flex-1 items-center justify-center bg-black px-5 py-10">
          <div className="absolute left-6 top-5 flex items-center gap-3">
            <div className="h-9 w-9 rounded-sm bg-[#3150D4] flex items-center justify-center text-white font-bold">
              C
            </div>
            <div>
              <p className="text-sm font-bold text-white">ClickClak</p>
              <p className="text-xs text-slate-500">Administrativo</p>
            </div>
          </div>

          <form
            onSubmit={manejarEnvio}
            className="w-full max-w-[430px] rounded-lg border border-[#123A5A] bg-[#051F3A] px-7 py-8 shadow-[0_22px_45px_rgba(0,0,0,0.32)]"
          >
            <div className="mb-7">
              <p className="text-xs font-semibold uppercase tracking-wide text-[#68BEEA]">Login</p>
              <h1 className="mt-2 text-2xl font-bold text-white">Panel administrativo</h1>
              <p className="mt-1 text-sm text-[#8DAAC2]">Supervisores y RRHH.</p>
            </div>

            <div className="grid gap-4">
              <label className="grid grid-cols-[120px_1fr] items-center gap-4 text-lg text-white">
                <span>Usuario:</span>
                <input
                  id="correo"
                  type="email"
                  required
                  autoComplete="username"
                  value={correo}
                  onChange={(evento) => setCorreo(evento.target.value)}
                  className="h-9 min-w-0 rounded-[2px] border border-transparent bg-white px-3 text-sm text-[#061229] outline-none transition focus:border-[#36C9FF] focus:ring-2 focus:ring-[#36C9FF]/25"
                />
              </label>

              <label className="grid grid-cols-[120px_1fr] items-center gap-4 text-lg text-white">
                <span>Contraseña:</span>
                <input
                  id="password"
                  type="password"
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(evento) => setPassword(evento.target.value)}
                  className="h-9 min-w-0 rounded-[2px] border border-transparent bg-white px-3 text-sm text-[#061229] outline-none transition focus:border-[#36C9FF] focus:ring-2 focus:ring-[#36C9FF]/25"
                />
              </label>
            </div>

            {error && (
              <p className="mt-5 rounded-sm border border-[#F58989]/30 bg-[#3B1018] px-3 py-2 text-sm text-[#FFC4C4]">
                {error}
              </p>
            )}

            <div className="mt-8 flex justify-end">
              <button
                type="submit"
                disabled={enviando}
                className="h-10 min-w-[190px] rounded-sm bg-[#062B46] px-6 text-lg font-medium text-white shadow-[0_5px_0_rgba(0,0,0,0.35)] transition hover:bg-[#0A4167] focus:outline-none focus:ring-2 focus:ring-[#36C9FF]/40 disabled:opacity-60"
              >
                {enviando ? "Ingresando..." : "Ingresar"}
              </button>
            </div>
          </form>
        </main>

        <aside className="hidden flex-1 items-center justify-center border-l-2 border-[#139DFF] bg-[#062542] md:flex">
          <div className="relative h-[240px] w-[240px]">
            <div className="absolute inset-8 rounded-full bg-[#03182B] shadow-[0_0_70px_rgba(0,209,255,0.18)]" />
            <div className="absolute inset-[54px] rounded-full border border-[#00C8FF]/45 bg-[#073856] shadow-[inset_0_0_36px_rgba(0,200,255,0.26),0_0_38px_rgba(0,200,255,0.26)]" />
            <svg viewBox="0 0 240 240" className="relative h-full w-full text-[#36D6FF]">
              <defs>
                <clipPath id="admin-fingerprint-clip">
                  <circle cx="120" cy="120" r="68" />
                </clipPath>
              </defs>
              <circle cx="120" cy="120" r="68" stroke="currentColor" strokeWidth="1" opacity=".55" fill="none" />
              <circle cx="120" cy="120" r="53" stroke="currentColor" strokeWidth="1" opacity=".35" fill="none" strokeDasharray="42 18" />
              <circle cx="120" cy="120" r="75" stroke="currentColor" strokeWidth="1" opacity=".25" fill="none" strokeDasharray="5 14" />
              <g stroke="currentColor" strokeWidth="3" fill="none" strokeLinecap="round" opacity=".95">
                <path d="M88 123c0-20 14-34 32-34s32 14 32 34" />
                <path d="M96 123c0-15 10-26 24-26s24 11 24 26" />
                <path d="M104 123c0-10 7-18 16-18s16 8 16 18" />
                <path d="M112 123c0-6 4-10 8-10s8 4 8 10" />
                <path d="M88 132c2 18 15 29 32 29" />
                <path d="M97 132c2 13 11 21 23 21" />
                <path d="M107 132c2 8 7 13 13 13" />
                <path d="M121 124c-2 14 3 24 17 31" />
                <path d="M133 130c1 8 5 14 12 18" />
              </g>
              <line
                className="animate-escaneo-huella"
                x1="45"
                y1="120"
                x2="195"
                y2="120"
                stroke="currentColor"
                strokeWidth="3"
                clipPath="url(#admin-fingerprint-clip)"
              />
              <g fill="currentColor">
                <circle cx="76" cy="82" r="1.6" opacity=".9" />
                <circle cx="173" cy="78" r="1.2" opacity=".65" />
                <circle cx="183" cy="157" r="1.4" opacity=".7" />
                <circle cx="70" cy="154" r="1.2" opacity=".55" />
              </g>
            </svg>
          </div>
        </aside>
      </div>
    </div>
  );
}
