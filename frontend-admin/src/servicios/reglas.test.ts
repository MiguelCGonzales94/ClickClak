import { describe, expect, it } from "vitest";
import type { RespuestaProyecto } from "../types/api";
import {
  aSolicitudServicio,
  hayCambiosServicio,
  soloActivos,
  validarServicio,
  valoresDeServicio,
  valoresServicioVacios,
} from "./reglas";

function proyecto(parcial: Partial<RespuestaProyecto> = {}): RespuestaProyecto {
  return { id: 1, nombre: "ATANKA", cliente: "FELICITA", fechaInicio: "2026-10-09", fechaFin: null, activo: true, ...parcial };
}

describe("validarServicio", () => {
  const completo = { ...valoresServicioVacios("2026-10-01"), nombre: "Servicio", cliente: "Cliente" };

  it("acepta un formulario completo, con o sin fecha de fin", () => {
    expect(validarServicio(completo)).toBeNull();
    expect(validarServicio({ ...completo, fechaFin: "2026-12-31" })).toBeNull();
  });

  it("pide nombre, cliente y fecha de inicio", () => {
    expect(validarServicio({ ...completo, nombre: "  " })).toMatch(/nombre/i);
    expect(validarServicio({ ...completo, cliente: "" })).toMatch(/cliente/i);
    expect(validarServicio({ ...completo, fechaInicio: "" })).toMatch(/inicio/i);
  });

  it("rechaza un fin anterior al inicio", () => {
    expect(validarServicio({ ...completo, fechaFin: "2026-09-30" })).toMatch(/anterior/i);
  });
});

describe("valores y solicitud", () => {
  it("una fecha de fin vacía se envía como null y los textos se recortan", () => {
    const v = { nombre: "  A ", cliente: " B ", fechaInicio: "2026-10-01", fechaFin: "" };
    expect(aSolicitudServicio(v)).toEqual({ nombre: "A", cliente: "B", fechaInicio: "2026-10-01", fechaFin: null });
  });

  it("reabrir con los valores del servicio no cuenta como cambio", () => {
    const p = proyecto();
    expect(hayCambiosServicio(p, valoresDeServicio(p))).toBe(false);
  });

  it("detecta cada campo", () => {
    const p = proyecto();
    const base = valoresDeServicio(p);
    expect(hayCambiosServicio(p, { ...base, nombre: "Otro" })).toBe(true);
    expect(hayCambiosServicio(p, { ...base, cliente: "Otro" })).toBe(true);
    expect(hayCambiosServicio(p, { ...base, fechaInicio: "2026-10-10" })).toBe(true);
    expect(hayCambiosServicio(p, { ...base, fechaFin: "2026-12-31" })).toBe(true);
  });
});

describe("soloActivos", () => {
  const items = [
    { id: 1, activo: true },
    { id: 2, activo: false },
    { id: 3, activo: false },
  ];

  it("deja solo lo activo", () => {
    expect(soloActivos(items).map((i) => i.id)).toEqual([1]);
  });

  it("conserva el elemento que la asignación ya usa aunque esté desactivado", () => {
    expect(soloActivos(items, 3).map((i) => i.id)).toEqual([1, 3]);
  });
});
