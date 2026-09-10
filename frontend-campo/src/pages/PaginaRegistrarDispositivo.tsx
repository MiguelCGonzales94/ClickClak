import { useEffect, useState, type FormEvent } from "react";
import { ErrorHttp } from "../services/clienteApi";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { servicioWebAuthn, soportaWebAuthn } from "../services/servicioWebAuthn";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaUsuario } from "../types/api";

/**
 * HU01/HU05: enrolamiento de un dispositivo. Lo ejecuta un Supervisor/RRHH con el técnico
 * presente frente a este teléfono — el técnico pone su huella/rostro en el paso del
 * navegador, pero quien autoriza la operación (y cuya sesión queda registrada) es el
 * supervisor. El listado de técnicos viene de HU04 (gestión de usuarios).
 */
export function PaginaRegistrarDispositivo() {
  const { token } = useSesion();
  const [tecnicos, setTecnicos] = useState<RespuestaUsuario[]>([]);
  const [cargandoTecnicos, setCargandoTecnicos] = useState(true);
  const [usuarioId, setUsuarioId] = useState("");
  const [nombreDispositivo, setNombreDispositivo] = useState("");
  const [estado, setEstado] = useState<"inactivo" | "en-progreso" | "exito">("inactivo");
  const [error, setError] = useState<string | null>(null);

  const compatible = soportaWebAuthn();

  useEffect(() => {
    if (!token) return;
    servicioUsuarios
      .listar(token, "COLABORADOR", true)
      .then(setTecnicos)
      .catch((err) => setError(err instanceof ErrorHttp ? err.message : "No se pudo cargar la lista de técnicos"))
      .finally(() => setCargandoTecnicos(false));
  }, [token]);

  async function manejarEnvio(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;

    setError(null);
    setEstado("en-progreso");
    try {
      await servicioWebAuthn.registrarDispositivo(Number(usuarioId), nombreDispositivo, token);
      setEstado("exito");
    } catch (err) {
      setEstado("inactivo");
      setError(err instanceof ErrorHttp ? err.message : "No se pudo completar el registro del dispositivo");
    }
  }

  return (
    <div className="min-h-screen bg-fondo px-4 py-10 flex flex-col items-center gap-6">
      <div className="w-full max-w-sm flex flex-col gap-1 text-center">
        <h1 className="text-xl font-bold text-texto">Registrar dispositivo</h1>
        <p className="text-sm text-textoSuave">
          Con el técnico presente: elige su nombre y pídele su huella o rostro cuando el navegador lo solicite.
        </p>
      </div>

      {!compatible && (
        <p className="w-full max-w-sm text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
          Este navegador no admite registro biométrico WebAuthn. Usa un Android con Chrome actualizado.
        </p>
      )}

      {estado === "exito" ? (
        <p className="w-full max-w-sm text-sm text-exitoTexto bg-exitoFondo border border-exito/30 rounded-sm px-3 py-2">
          Dispositivo registrado correctamente. El técnico ya puede iniciar sesión con su huella o rostro.
        </p>
      ) : (
        <form onSubmit={manejarEnvio} className="w-full max-w-sm bg-superficie rounded-md shadow-tarjeta p-6 flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label htmlFor="usuarioId" className="text-sm font-semibold text-texto">
              Técnico
            </label>
            <select
              id="usuarioId"
              required
              value={usuarioId}
              onChange={(evento) => setUsuarioId(evento.target.value)}
              disabled={cargandoTecnicos}
              className="h-12 px-3 rounded-sm border border-borde text-sm bg-superficie focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario disabled:opacity-60"
            >
              <option value="" disabled>
                {cargandoTecnicos ? "Cargando técnicos…" : "Selecciona un técnico"}
              </option>
              {tecnicos.map((tecnico) => (
                <option key={tecnico.id} value={tecnico.id}>
                  {tecnico.nombres} {tecnico.apellidos} — {tecnico.correo}
                </option>
              ))}
            </select>
            {!cargandoTecnicos && tecnicos.length === 0 && (
              <p className="text-xs text-textoSuave">
                No hay técnicos activos registrados todavía. Da de alta uno primero (HU04).
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="nombreDispositivo" className="text-sm font-semibold text-texto">
              Nombre del dispositivo (opcional)
            </label>
            <input
              id="nombreDispositivo"
              type="text"
              placeholder="Teléfono de campo"
              value={nombreDispositivo}
              onChange={(evento) => setNombreDispositivo(evento.target.value)}
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
            disabled={!compatible || estado === "en-progreso" || !usuarioId}
            className="h-12 rounded-sm bg-primario text-white font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
          >
            {estado === "en-progreso" ? "Esperando biometría…" : "Registrar dispositivo"}
          </button>
        </form>
      )}
    </div>
  );
}
