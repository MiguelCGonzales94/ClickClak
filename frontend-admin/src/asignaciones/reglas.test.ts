import { describe, expect, it } from "vitest";
import type { RespuestaAsignacion, RespuestaUbicacion } from "../types/api";
import {
  accionesDisponibles,
  clasesEstado,
  coincideConFiltro,
  describirTraslado,
  etiquetaEstado,
  fechaDeCambioInicial,
  fechaMaximaDeCambio,
  fechaMinimaDeCambio,
  filtrarAsignaciones,
  hayCambios,
  sedesDelServicio,
  sedesVigentesPorTecnico,
  validarFechaDeCambio,
  validarFechas,
} from "./reglas";

function asignacion(parcial: Partial<RespuestaAsignacion>): RespuestaAsignacion {
  return {
    id: 1,
    usuarioId: 10,
    nombreUsuario: "Ana Pérez",
    proyectoId: 1,
    nombreProyecto: "Servicio Uno",
    ubicacionId: 100,
    nombreUbicacion: "Sede A",
    horarioId: 5,
    nombreHorario: "Turno mañana",
    fechaInicio: "2026-03-01",
    fechaFin: null,
    estado: "VIGENTE",
    ...parcial,
  };
}

function sede(id: number, proyectoId: number): RespuestaUbicacion {
  return {
    id,
    proyectoId,
    nombre: `Sede ${id}`,
    direccionReferencia: null,
    latitud: 0,
    longitud: 0,
    radioToleranciaMetros: 150,
    activo: true,
  };
}

describe("estados de la asignación", () => {
  it("traduce y colorea cada estado", () => {
    expect(etiquetaEstado("VIGENTE")).toBe("Vigente");
    expect(etiquetaEstado("PROGRAMADA")).toBe("Programada");
    expect(etiquetaEstado("FINALIZADA")).toBe("Finalizada");
    expect(clasesEstado("VIGENTE")).toContain("exito");
    expect(clasesEstado("PROGRAMADA")).toContain("primario");
    expect(clasesEstado("FINALIZADA")).toContain("textoSuave");
  });
});

describe("filtrarAsignaciones", () => {
  const lista = [
    asignacion({ id: 1, usuarioId: 10, estado: "VIGENTE" }),
    asignacion({ id: 2, usuarioId: 10, estado: "FINALIZADA" }),
    asignacion({ id: 3, usuarioId: 20, estado: "PROGRAMADA" }),
  ];

  it("por defecto muestra lo vigente y lo programado, no lo finalizado", () => {
    expect(filtrarAsignaciones(lista, { usuarioId: "", estado: "ACTIVAS" }).map((a) => a.id)).toEqual([1, 3]);
  });

  it("puede pedir un estado concreto o todas", () => {
    expect(filtrarAsignaciones(lista, { usuarioId: "", estado: "FINALIZADA" }).map((a) => a.id)).toEqual([2]);
    expect(filtrarAsignaciones(lista, { usuarioId: "", estado: "TODAS" })).toHaveLength(3);
    expect(coincideConFiltro(lista[2], "PROGRAMADA")).toBe(true);
  });

  it("filtra por técnico y combina con el estado", () => {
    expect(filtrarAsignaciones(lista, { usuarioId: "10", estado: "TODAS" }).map((a) => a.id)).toEqual([1, 2]);
    expect(filtrarAsignaciones(lista, { usuarioId: "10", estado: "ACTIVAS" }).map((a) => a.id)).toEqual([1]);
    expect(filtrarAsignaciones(lista, { usuarioId: "99", estado: "TODAS" })).toEqual([]);
  });
});

describe("sedesVigentesPorTecnico", () => {
  it("cuenta solo las vigentes, por técnico", () => {
    const cuenta = sedesVigentesPorTecnico([
      asignacion({ id: 1, usuarioId: 10, estado: "VIGENTE" }),
      asignacion({ id: 2, usuarioId: 10, estado: "VIGENTE" }),
      asignacion({ id: 3, usuarioId: 10, estado: "FINALIZADA" }),
      asignacion({ id: 4, usuarioId: 10, estado: "PROGRAMADA" }),
      asignacion({ id: 5, usuarioId: 20, estado: "VIGENTE" }),
    ]);

    expect(cuenta).toEqual({ 10: 2, 20: 1 });
  });
});

describe("sedesDelServicio", () => {
  it("devuelve solo las sedes del servicio elegido", () => {
    const sedes = [sede(1, 1), sede(2, 1), sede(3, 2)];

    expect(sedesDelServicio(sedes, "1").map((s) => s.id)).toEqual([1, 2]);
    expect(sedesDelServicio(sedes, 2).map((s) => s.id)).toEqual([3]);
    expect(sedesDelServicio(sedes, "")).toEqual([]);
  });
});

describe("validarFechas", () => {
  it("exige inicio y no admite un fin anterior", () => {
    expect(validarFechas("2026-03-01", "")).toBeNull();
    expect(validarFechas("2026-03-01", "2026-03-01")).toBeNull();
    expect(validarFechas("", "")).not.toBeNull();
    expect(validarFechas("2026-03-10", "2026-03-01")).not.toBeNull();
  });
});

describe("acciones disponibles", () => {
  it("una finalizada se edita o se quita, pero no se mueve", () => {
    expect(accionesDisponibles(asignacion({ estado: "FINALIZADA" }))).toEqual({ editar: true, mover: false, quitar: true });
    expect(accionesDisponibles(asignacion({ estado: "VIGENTE" }))).toEqual({ editar: true, mover: true, quitar: true });
    expect(accionesDisponibles(asignacion({ estado: "PROGRAMADA" })).mover).toBe(true);
  });
});

describe("traslado a otra sede", () => {
  const actual = asignacion({ fechaInicio: "2026-03-01", fechaFin: "2026-12-31" });

  it("el rango permitido va del día siguiente al inicio hasta el fin", () => {
    expect(fechaMinimaDeCambio(actual)).toBe("2026-03-02");
    expect(fechaMaximaDeCambio(actual)).toBe("2026-12-31");
    expect(fechaMaximaDeCambio(asignacion({ fechaFin: null }))).toBeUndefined();
  });

  it("propone hoy si cae en el rango y, si no, el borde más cercano", () => {
    expect(fechaDeCambioInicial(actual, "2026-06-15")).toBe("2026-06-15");
    expect(fechaDeCambioInicial(actual, "2026-03-01")).toBe("2026-03-02");
    expect(fechaDeCambioInicial(actual, "2027-02-01")).toBe("2026-12-31");
    expect(fechaDeCambioInicial(asignacion({ fechaFin: null }), "2030-01-01")).toBe("2030-01-01");
  });

  it("valida la fecha de cambio contra el inicio y el fin", () => {
    expect(validarFechaDeCambio(actual, "2026-06-01")).toBeNull();
    expect(validarFechaDeCambio(actual, "")).not.toBeNull();
    expect(validarFechaDeCambio(actual, "2026-03-01")).toContain("posterior al inicio");
    expect(validarFechaDeCambio(actual, "2027-01-01")).toContain("termina el 2026-12-31");
  });

  it("explica qué hará: termina la actual el día anterior y empieza la nueva heredando el fin", () => {
    expect(describirTraslado(actual, "2026-06-01", "Sede B")).toBe(
      "Sede A termina el 2026-05-31 y Sede B empieza el 2026-06-01, hasta el 2026-12-31.",
    );
    expect(describirTraslado(asignacion({ fechaFin: null }), "2026-06-01", "")).toContain("sin fecha de término");
    expect(describirTraslado(actual, "2026-03-02", "Sede B")).toContain("termina el 2026-03-01");
  });
});

describe("hayCambios", () => {
  const original = asignacion({ fechaFin: null });
  const igual = {
    proyectoId: 1,
    ubicacionId: 100,
    horarioId: 5,
    fechaInicio: "2026-03-01",
    fechaFin: null,
  };

  it("detecta una edición sin cambios y cada campo que cambia", () => {
    expect(hayCambios(original, igual)).toBe(false);
    expect(hayCambios(original, { ...igual, ubicacionId: 101 })).toBe(true);
    expect(hayCambios(original, { ...igual, horarioId: 6 })).toBe(true);
    expect(hayCambios(original, { ...igual, proyectoId: 2 })).toBe(true);
    expect(hayCambios(original, { ...igual, fechaInicio: "2026-03-02" })).toBe(true);
    expect(hayCambios(original, { ...igual, fechaFin: "2026-12-31" })).toBe(true);
    expect(hayCambios(original, { ...igual, fechaFin: undefined })).toBe(false);
  });
});
