import type { ErrorApi } from "../types/api";

const URL_BASE = import.meta.env.VITE_API_URL ?? "";

export class ErrorHttp extends Error {
  constructor(
    public readonly status: number,
    mensaje: string,
  ) {
    super(mensaje);
  }
}

interface OpcionesSolicitud {
  token?: string | null;
  cuerpo?: unknown;
}

async function solicitar<T>(
  metodo: "GET" | "POST" | "PUT",
  ruta: string,
  { token, cuerpo }: OpcionesSolicitud = {},
): Promise<T> {
  const cabeceras: Record<string, string> = {};
  if (cuerpo !== undefined) {
    cabeceras["Content-Type"] = "application/json";
  }
  if (token) {
    cabeceras["Authorization"] = `Bearer ${token}`;
  }

  const respuesta = await fetch(`${URL_BASE}${ruta}`, {
    method: metodo,
    headers: cabeceras,
    body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
  });

  if (!respuesta.ok) {
    let mensaje = `Error de red (${respuesta.status})`;
    try {
      const cuerpoError = (await respuesta.json()) as ErrorApi;
      mensaje = cuerpoError.error ?? mensaje;
    } catch {
      // El backend siempre responde JSON en sus errores; si no pudo parsearse
      // se conserva el mensaje genérico anterior.
    }
    throw new ErrorHttp(respuesta.status, mensaje);
  }

  if (respuesta.status === 204) {
    return undefined as T;
  }
  return (await respuesta.json()) as T;
}

export const clienteApi = {
  get: <T>(ruta: string, token?: string | null) => solicitar<T>("GET", ruta, { token }),
  post: <T>(ruta: string, cuerpo: unknown, token?: string | null) =>
    solicitar<T>("POST", ruta, { token, cuerpo }),
  put: <T>(ruta: string, cuerpo: unknown, token?: string | null) =>
    solicitar<T>("PUT", ruta, { token, cuerpo }),
};
