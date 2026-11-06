import { describe, expect, it } from "vitest";
import type { RespuestaUbicacion } from "../types/api";
import {
  formatearCoordenadas,
  hayCambios,
  interpretarCoordenadas,
  limitesDeZona,
  puntoDeValores,
  radioDeValores,
  redondear,
  sedesVecinas,
  validarSede,
  valoresDeSede,
  valoresVacios,
} from "./reglas";

function sede(parcial: Partial<RespuestaUbicacion> = {}): RespuestaUbicacion {
  return {
    id: 1,
    proyectoId: 7,
    nombre: "Almacén Norte",
    direccionReferencia: "Av. Principal 123",
    latitud: -12.046374,
    longitud: -77.042793,
    radioToleranciaMetros: 150,
    activo: true,
    ...parcial,
  };
}

describe("interpretarCoordenadas", () => {
  it("lee lo que copia Google Maps", () => {
    expect(interpretarCoordenadas("-12.046374, -77.042793")).toEqual({ latitud: -12.046374, longitud: -77.042793 });
  });

  it("acepta espacio, punto y coma y coma decimal", () => {
    expect(interpretarCoordenadas("-12.04 -77.04")).toEqual({ latitud: -12.04, longitud: -77.04 });
    expect(interpretarCoordenadas("-12,0464; -77,0428")).toEqual({ latitud: -12.0464, longitud: -77.0428 });
    expect(interpretarCoordenadas("  -12 , -77  ")).toEqual({ latitud: -12, longitud: -77 });
  });

  it("rechaza texto, un solo número y valores fuera de rango", () => {
    expect(interpretarCoordenadas("")).toBeNull();
    expect(interpretarCoordenadas("Lima")).toBeNull();
    expect(interpretarCoordenadas("-12.04")).toBeNull();
    expect(interpretarCoordenadas("91, -77")).toBeNull();
    expect(interpretarCoordenadas("-12, 181")).toBeNull();
    expect(interpretarCoordenadas("-12, -77, 5")).toBeNull();
  });
});

describe("formato", () => {
  it("redondea a seis decimales y formatea con la misma precisión", () => {
    expect(redondear(-12.04637449)).toBe(-12.046374);
    expect(formatearCoordenadas(-12.0464, -77.0428)).toBe("-12.046400, -77.042800");
  });
});

describe("puntoDeValores y radioDeValores", () => {
  it("solo devuelve punto con latitud y longitud válidas", () => {
    expect(puntoDeValores({ latitud: "-12.04", longitud: "-77.04" })).toEqual({ latitud: -12.04, longitud: -77.04 });
    expect(puntoDeValores({ latitud: "", longitud: "-77" })).toBeNull();
    expect(puntoDeValores({ latitud: "95", longitud: "-77" })).toBeNull();
    expect(puntoDeValores({ latitud: "abc", longitud: "-77" })).toBeNull();
  });

  it("el radio es un entero entre 1 y 5000", () => {
    expect(radioDeValores("150")).toBe(150);
    expect(radioDeValores("0")).toBeNull();
    expect(radioDeValores("5001")).toBeNull();
    expect(radioDeValores("12.5")).toBeNull();
    expect(radioDeValores("")).toBe(null);
  });
});

describe("validarSede", () => {
  const completo = { ...valoresVacios("7"), nombre: "Sede", latitud: "-12.04", longitud: "-77.04" };

  it("acepta un formulario completo", () => {
    expect(validarSede(completo, false)).toBeNull();
  });

  it("pide el servicio solo al crear", () => {
    expect(validarSede({ ...completo, proyectoId: "" }, false)).toMatch(/servicio/i);
    expect(validarSede({ ...completo, proyectoId: "" }, true)).toBeNull();
  });

  it("no deja guardar sin nombre, sin zona marcada o con un radio inválido", () => {
    expect(validarSede({ ...completo, nombre: "  " }, false)).toMatch(/nombre/i);
    expect(validarSede({ ...completo, latitud: "", longitud: "" }, false)).toMatch(/zona/i);
    expect(validarSede({ ...completo, latitud: "120" }, false)).toMatch(/zona/i);
    expect(validarSede({ ...completo, radioToleranciaMetros: "0" }, false)).toMatch(/radio/i);
  });
});

describe("hayCambios", () => {
  it("no detecta cambios al reabrir con los valores de la sede", () => {
    const s = sede();
    expect(hayCambios(s, valoresDeSede(s))).toBe(false);
  });

  it("detecta nombre, referencia, centro y radio", () => {
    const s = sede();
    const base = valoresDeSede(s);
    expect(hayCambios(s, { ...base, nombre: "Otro" })).toBe(true);
    expect(hayCambios(s, { ...base, direccionReferencia: "" })).toBe(true);
    expect(hayCambios(s, { ...base, latitud: "-12.05" })).toBe(true);
    expect(hayCambios(s, { ...base, longitud: "-77.05" })).toBe(true);
    expect(hayCambios(s, { ...base, radioToleranciaMetros: "200" })).toBe(true);
  });

  it("una referencia vacía equivale a ninguna y el redondeo no cuenta como cambio", () => {
    const s = sede({ direccionReferencia: null });
    const base = valoresDeSede(s);
    expect(hayCambios(s, { ...base, direccionReferencia: "" })).toBe(false);
    expect(hayCambios(s, { ...base, latitud: "-12.0463740001" })).toBe(false);
  });
});

describe("sedesVecinas", () => {
  const sedes = [
    sede({ id: 1, proyectoId: 7 }),
    sede({ id: 2, proyectoId: 7 }),
    sede({ id: 3, proyectoId: 8 }),
    sede({ id: 4, proyectoId: 7, activo: false }),
  ];

  it("toma las activas del mismo servicio y excluye la que se edita", () => {
    expect(sedesVecinas(sedes, "7", 1).map((s) => s.id)).toEqual([2]);
    expect(sedesVecinas(sedes, "7").map((s) => s.id)).toEqual([1, 2]);
    expect(sedesVecinas(sedes, "").map((s) => s.id)).toEqual([]);
  });
});

describe("limitesDeZona", () => {
  it("encuadra el círculo con margen, más ancho en longitud lejos del ecuador", () => {
    const [[sur, oeste], [norte, este]] = limitesDeZona({ latitud: -12, longitud: -77 }, 1000);
    expect(sur).toBeLessThan(-12);
    expect(norte).toBeGreaterThan(-12);
    expect(oeste).toBeLessThan(-77);
    expect(este).toBeGreaterThan(-77);
    // 1000 m × 1,6 ≈ 0,0144° de latitud a cada lado.
    expect(norte - -12).toBeCloseTo(0.01437, 3);
  });
});
