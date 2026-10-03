import { clienteApi } from "./clienteApi";
import type { RespuestaDispositivo } from "../types/api";

/** Dispositivos WebAuthn activos asociados al colaborador autenticado. */
export const servicioDispositivos = {
  listarMios: (token: string) =>
    clienteApi.get<RespuestaDispositivo[]>("/api/dispositivos/mios", token),
};
