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
    <div className="min-h-screen flex">
      {/* Panel izquierdo: formulario sobre negro puro. */}
      <main className="flex-1 min-w-0 bg-loginNegro flex items-center justify-center px-6 py-10">
        <form onSubmit={manejarEnvio} className="w-full max-w-sm flex flex-col gap-6">
          <div className="flex flex-col items-center gap-2 mb-2 text-center">
            <div className="w-12 h-12 rounded-md bg-gradient-to-br from-primario to-acento flex items-center justify-center text-white font-bold text-lg">
              CC
            </div>
            <h1 className="text-xl font-bold text-textoClaro">ClickClak</h1>
            <p className="text-xs text-textoClaroSuave">Panel administrativo</p>
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="correo" className="text-sm text-textoClaro">
              Usuario:
            </label>
            <input
              id="correo"
              type="email"
              required
              autoComplete="username"
              value={correo}
              onChange={(evento) => setCorreo(evento.target.value)}
              className="h-11 px-3 rounded-sm bg-white text-texto text-sm focus:outline-none focus:ring-2 focus:ring-acento"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="password" className="text-sm text-textoClaro">
              Contraseña:
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(evento) => setPassword(evento.target.value)}
              className="h-11 px-3 rounded-sm bg-white text-texto text-sm focus:outline-none focus:ring-2 focus:ring-acento"
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
            className="h-11 mt-2 rounded-sm bg-panelOscuroHover border border-panelOscuroBorde text-textoClaro font-semibold hover:bg-primario hover:border-primario disabled:opacity-60 transition-colors"
          >
            {enviando ? "Ingresando…" : "Ingresar"}
          </button>
        </form>
      </main>

      {/* Panel derecho: huella animada sobre azul profundo — oculto en pantallas angostas. */}
      <aside className="hidden md:flex flex-1 items-center justify-center bg-gradient-to-br from-loginAzulProfundo to-loginAzulBrillo">
        <div className="relative w-52 h-52 flex items-center justify-center">
          <div
            className="absolute -inset-4 rounded-full blur-md"
            style={{ background: "radial-gradient(circle, rgba(18,179,166,.35), transparent 70%)" }}
          />
          <svg width="208" height="208" viewBox="0 0 240 240" className="relative">
            <circle cx="120" cy="130" r="112" stroke="rgba(255,255,255,.15)" strokeWidth="1" fill="none" />
            <circle cx="120" cy="130" r="84" stroke="rgba(255,255,255,.12)" strokeWidth="1" fill="none" />
            <g stroke="#5EEAD4" strokeWidth="3" strokeLinecap="round" fill="none">
              <path d="M30 130 A90 90 0 0 1 210 130" opacity="0.3" />
              <path d="M42 130 A78 78 0 0 1 198 130" opacity="0.45" />
              <path d="M54 130 A66 66 0 0 1 186 130" opacity="0.6" />
              <path d="M66 130 A54 54 0 0 1 174 130" opacity="0.75" />
              <path d="M78 130 A42 42 0 0 1 162 130" opacity="0.9" />
              <path d="M90 130 A30 30 0 0 1 150 130" opacity="1" />
            </g>
            <clipPath id="recorte-huella">
              <circle cx="120" cy="130" r="112" />
            </clipPath>
            <line
              className="animate-escaneo-huella"
              x1="14"
              y1="130"
              x2="226"
              y2="130"
              stroke="#5EEAD4"
              strokeWidth="2"
              clipPath="url(#recorte-huella)"
            />
          </svg>
        </div>
      </aside>
    </div>
  );
}
