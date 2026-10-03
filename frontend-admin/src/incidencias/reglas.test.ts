import { describe, expect, it } from "vitest";
import type { EstadoIncidencia, RespuestaIncidencia } from "../types/api";
import { formatearFecha, hoyEnLima } from "../utilidades/fechas";
import {
  accionesDisponibles,
  contarPorEstado,
  ESTADOS,
  esPendienteDeRevision,
  fechaMaximaDeEvento,
  filtrarIncidencias,
  motivoDeBloqueo,
  requiereComentario,
} from "./reglas";

function incidencia(parcial: Partial<RespuestaIncidencia>): RespuestaIncidencia {
  return {
    id: 1,
    usuarioId: 10,
    nombreUsuario: "Ana Pérez",
    marcacionId: null,
    tipo: "AUSENCIA",
    estado: "REGISTRADA",
    fechaEvento: "2026-10-01",
    descripcion: "No pudo asistir",
    creadoPorId: 10,
    nombreCreadoPor: "Ana Pérez",
    revisadoPorId: null,
    nombreRevisadoPor: null,
    comentarioRevision: null,
    revisadoEn: null,
    creadoEn: "2026-10-02T15:00:00Z",
    actualizadoEn: "2026-10-02T15:00:00Z",
    ...parcial,
  };
}

describe("flujo de estados", () => {
  it("cada estado ofrece solo las acciones que el flujo permite", () => {
    expect(accionesDisponibles("REGISTRADA")).toEqual(["iniciarRevision"]);
    expect(accionesDisponibles("EN_REVISION")).toEqual(["aprobar", "rechazar"]);
    expect(accionesDisponibles("APROBADA")).toEqual(["cerrar"]);
    expect(accionesDisponibles("RECHAZADA")).toEqual(["cerrar"]);
    expect(accionesDisponibles("CERRADA")).toEqual([]);
  });

  it("una incidencia cerrada es terminal", () => {
    expect(accionesDisponibles("CERRADA")).toHaveLength(0);
  });

  it("solo Registrada y En revisión están pendientes", () => {
    const pendientes = ESTADOS.filter(esPendienteDeRevision);
    expect(pendientes).toEqual(["REGISTRADA", "EN_REVISION"]);
  });

  it("solo rechazar exige un motivo", () => {
    expect(requiereComentario("rechazar")).toBe(true);
    expect(requiereComentario("aprobar")).toBe(false);
    expect(requiereComentario("iniciarRevision")).toBe(false);
    expect(requiereComentario("cerrar")).toBe(false);
  });
});

describe("separación de funciones", () => {
  const registradaPorElSupervisor = incidencia({ usuarioId: 10, creadoPorId: 20 });

  it("bloquea revisar una incidencia que afecta al propio revisor", () => {
    expect(motivoDeBloqueo("aprobar", registradaPorElSupervisor, 10)).not.toBeNull();
    expect(motivoDeBloqueo("iniciarRevision", registradaPorElSupervisor, 10)).not.toBeNull();
  });

  it("bloquea revisar lo que el propio revisor registró", () => {
    expect(motivoDeBloqueo("rechazar", registradaPorElSupervisor, 20)).not.toBeNull();
  });

  it("permite revisar a una tercera persona", () => {
    expect(motivoDeBloqueo("aprobar", registradaPorElSupervisor, 30)).toBeNull();
  });

  it("cerrar no está sujeto a la separación de funciones", () => {
    expect(motivoDeBloqueo("cerrar", registradaPorElSupervisor, 20)).toBeNull();
    expect(motivoDeBloqueo("cerrar", registradaPorElSupervisor, 10)).toBeNull();
  });
});

describe("fecha del evento", () => {
  it("solo un permiso puede ser a futuro", () => {
    expect(fechaMaximaDeEvento("PERMISO", "2026-10-02")).toBeUndefined();
    expect(fechaMaximaDeEvento("TARDANZA", "2026-10-02")).toBe("2026-10-02");
    expect(fechaMaximaDeEvento("AUSENCIA", "2026-10-02")).toBe("2026-10-02");
  });

  it("hoy se calcula en Lima, no en UTC", () => {
    // 03:00 UTC del 3 de octubre son las 22:00 del 2 de octubre en Lima (UTC-5, sin horario de verano).
    expect(hoyEnLima(new Date("2026-10-03T03:00:00Z"))).toBe("2026-10-02");
    expect(hoyEnLima(new Date("2026-10-03T05:00:00Z"))).toBe("2026-10-03");
  });

  it("formatea la fecha sin convertir de zona", () => {
    expect(formatearFecha("2026-10-02")).toBe("02/10/2026");
  });
});

describe("conteo y filtros", () => {
  const lista = [
    incidencia({ id: 1, estado: "REGISTRADA", nombreUsuario: "Ana Pérez", tipo: "AUSENCIA" }),
    incidencia({ id: 2, estado: "REGISTRADA", nombreUsuario: "Luis Gómez", tipo: "TARDANZA" }),
    incidencia({ id: 3, estado: "EN_REVISION", nombreUsuario: "Ana Pérez", tipo: "PERMISO", descripcion: "Cita médica" }),
    incidencia({ id: 4, estado: "CERRADA", nombreUsuario: "José Ñañez", tipo: "OLVIDO_REGISTRO" }),
  ];

  it("cuenta por estado y deja en cero los vacíos", () => {
    expect(contarPorEstado(lista)).toEqual({
      REGISTRADA: 2,
      EN_REVISION: 1,
      APROBADA: 0,
      RECHAZADA: 0,
      CERRADA: 1,
    });
  });

  it("filtra por estado", () => {
    expect(filtrarIncidencias(lista, { estado: "REGISTRADA" }).map((i) => i.id)).toEqual([1, 2]);
    expect(filtrarIncidencias(lista, { estado: null }).map((i) => i.id)).toEqual([1, 2, 3, 4]);
  });

  it("busca sin distinguir tildes ni mayúsculas", () => {
    expect(filtrarIncidencias(lista, { texto: "perez" }).map((i) => i.id)).toEqual([1, 3]);
    expect(filtrarIncidencias(lista, { texto: "JOSE" }).map((i) => i.id)).toEqual([4]);
    expect(filtrarIncidencias(lista, { texto: "ñañez" }).map((i) => i.id)).toEqual([4]);
  });

  it("busca también por tipo y por descripción", () => {
    expect(filtrarIncidencias(lista, { texto: "olvido" }).map((i) => i.id)).toEqual([4]);
    expect(filtrarIncidencias(lista, { texto: "medica" }).map((i) => i.id)).toEqual([3]);
  });

  it("combina estado y texto", () => {
    const estado: EstadoIncidencia = "REGISTRADA";
    expect(filtrarIncidencias(lista, { estado, texto: "ana" }).map((i) => i.id)).toEqual([1]);
  });

  it("un texto vacío o con espacios no filtra", () => {
    expect(filtrarIncidencias(lista, { texto: "   " })).toHaveLength(4);
  });
});
