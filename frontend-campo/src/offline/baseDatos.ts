import Dexie, { type Table } from "dexie";
import type { RespuestaMarcacion, SolicitudRegistrarMarcacion } from "../types/api";

export type EstadoMarcacionOffline = "PENDIENTE" | "SINCRONIZANDO" | "SINCRONIZADA" | "ERROR";

export interface MarcacionOffline {
  id?: number;
  uuidCliente: string;
  usuarioId: number;
  solicitud: SolicitudRegistrarMarcacion;
  estado: EstadoMarcacionOffline;
  intentos: number;
  creadaEn: string;
  actualizadaEn: string;
  sincronizadaEn?: string;
  ultimoError?: string;
  respuesta?: RespuestaMarcacion;
}

class BaseDatosClickClakCampo extends Dexie {
  marcaciones!: Table<MarcacionOffline, number>;

  constructor() {
    super("clickclak-campo");
    this.version(1).stores({
      marcaciones: "++id,&uuidCliente,usuarioId,estado,creadaEn",
    });
  }
}

export const baseDatosOffline = new BaseDatosClickClakCampo();
