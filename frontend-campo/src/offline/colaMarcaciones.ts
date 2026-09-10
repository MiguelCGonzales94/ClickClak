import { ErrorHttp } from "../services/clienteApi";
import { servicioMarcaciones } from "../services/servicioMarcaciones";
import type { SolicitudRegistrarMarcacion } from "../types/api";
import { baseDatosOffline, type MarcacionOffline } from "./baseDatos";

export interface ResumenSincronizacion {
  sincronizadas: number;
  conError: number;
  pendientes: number;
}

export function esErrorReintentable(error: unknown): boolean {
  if (!(error instanceof ErrorHttp)) {
    return true;
  }
  return error.status === 408 || error.status === 429 || error.status >= 500;
}

export function mensajeDeError(error: unknown): string {
  if (error instanceof ErrorHttp) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "No se pudo sincronizar la marcación.";
}

export async function encolarMarcacion(
  usuarioId: number,
  solicitud: SolicitudRegistrarMarcacion,
  error?: unknown,
): Promise<MarcacionOffline> {
  const ahora = new Date().toISOString();
  const existente = await baseDatosOffline.marcaciones
    .where("uuidCliente")
    .equals(solicitud.uuidCliente)
    .first();

  if (existente) {
    return existente;
  }

  const id = await baseDatosOffline.marcaciones.add({
    uuidCliente: solicitud.uuidCliente,
    usuarioId,
    solicitud,
    estado: "PENDIENTE",
    intentos: 0,
    creadaEn: ahora,
    actualizadaEn: ahora,
    ultimoError: error ? mensajeDeError(error) : undefined,
  });

  return (await baseDatosOffline.marcaciones.get(id)) as MarcacionOffline;
}

export async function listarMarcacionesOffline(usuarioId: number): Promise<MarcacionOffline[]> {
  const marcaciones = await baseDatosOffline.marcaciones.where("usuarioId").equals(usuarioId).toArray();
  return marcaciones.sort((actual, siguiente) => siguiente.creadaEn.localeCompare(actual.creadaEn));
}

export async function contarMarcacionesPendientes(usuarioId: number): Promise<number> {
  const marcaciones = await listarMarcacionesOffline(usuarioId);
  return marcaciones.filter((marcacion) => marcacion.estado === "PENDIENTE" || marcacion.estado === "ERROR").length;
}

export async function sincronizarMarcacionesPendientes(
  token: string,
  usuarioId: number,
): Promise<ResumenSincronizacion> {
  const candidatas = (await listarMarcacionesOffline(usuarioId))
    .filter((marcacion) => marcacion.estado === "PENDIENTE" || marcacion.estado === "ERROR")
    .reverse();

  let sincronizadas = 0;
  let conError = 0;

  for (const marcacion of candidatas) {
    if (!marcacion.id) continue;

    await baseDatosOffline.marcaciones.update(marcacion.id, {
      estado: "SINCRONIZANDO",
      actualizadaEn: new Date().toISOString(),
    });

    try {
      const respuesta = await servicioMarcaciones.registrar(token, marcacion.solicitud);
      await baseDatosOffline.marcaciones.update(marcacion.id, {
        estado: "SINCRONIZADA",
        respuesta,
        sincronizadaEn: new Date().toISOString(),
        actualizadaEn: new Date().toISOString(),
        ultimoError: undefined,
      });
      sincronizadas += 1;
    } catch (error) {
      await baseDatosOffline.marcaciones.update(marcacion.id, {
        estado: "ERROR",
        intentos: marcacion.intentos + 1,
        ultimoError: mensajeDeError(error),
        actualizadaEn: new Date().toISOString(),
      });
      conError += 1;

      if (error instanceof ErrorHttp && (error.status === 401 || error.status === 403)) {
        break;
      }
    }
  }

  return {
    sincronizadas,
    conError,
    pendientes: await contarMarcacionesPendientes(usuarioId),
  };
}
