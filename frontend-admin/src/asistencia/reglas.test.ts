import { describe, expect, it } from "vitest";
import {
  clasesEstado,
  describirRetraso,
  ESTADOS_VALIDACION,
  etiquetaEstado,
  etiquetaTipo,
  explicacionEstado,
  filtrosIniciales,
  formatearMetros,
  rangoRapido,
  seSincronizoDespues,
  sumarDias,
  totalPorRevisar,
  urlMapa,
  validarRango,
} from "./reglas";

describe("sumarDias", () => {
  it("suma y resta días dentro del mes", () => {
    expect(sumarDias("2026-10-09", 1)).toBe("2026-10-10");
    expect(sumarDias("2026-10-09", -6)).toBe("2026-10-03");
  });

  it("cruza meses y años", () => {
    expect(sumarDias("2026-10-01", -1)).toBe("2026-09-30");
    expect(sumarDias("2026-01-01", -1)).toBe("2025-12-31");
    expect(sumarDias("2026-12-31", 1)).toBe("2027-01-01");
  });

  it("respeta el año bisiesto", () => {
    expect(sumarDias("2028-03-01", -1)).toBe("2028-02-29");
    expect(sumarDias("2026-03-01", -1)).toBe("2026-02-28");
  });
});

describe("rangoRapido", () => {
  it("hoy y ayer son un solo día", () => {
    expect(rangoRapido("hoy", "2026-10-09")).toEqual({ desde: "2026-10-09", hasta: "2026-10-09" });
    expect(rangoRapido("ayer", "2026-10-09")).toEqual({ desde: "2026-10-08", hasta: "2026-10-08" });
  });

  it("los últimos 7 días incluyen hoy", () => {
    expect(rangoRapido("7dias", "2026-10-09")).toEqual({ desde: "2026-10-03", hasta: "2026-10-09" });
  });
});

describe("filtrosIniciales y validarRango", () => {
  it("arranca en el día de hoy, sin otros filtros y en la primera página", () => {
    expect(filtrosIniciales("2026-10-09")).toEqual({
      usuarioId: "",
      ubicacionId: "",
      desde: "2026-10-09",
      hasta: "2026-10-09",
      estado: "",
      tipoEvento: "",
      pagina: 0,
      tamano: 20,
    });
  });

  it("acepta rangos coherentes o abiertos y rechaza una fecha inicial posterior a la final", () => {
    expect(validarRango("2026-10-01", "2026-10-09")).toBeNull();
    expect(validarRango("2026-10-09", "2026-10-09")).toBeNull();
    expect(validarRango("", "2026-10-09")).toBeNull();
    expect(validarRango("2026-10-09", "")).toBeNull();
    expect(validarRango("2026-10-10", "2026-10-09")).not.toBeNull();
  });
});

describe("describirRetraso y seSincronizoDespues", () => {
  it("menos de un minuto es inmediata", () => {
    expect(describirRetraso(0)).toBe("Inmediata");
    expect(describirRetraso(59)).toBe("Inmediata");
    expect(seSincronizoDespues({ retrasoSincronizacionSegundos: 59 })).toBe(false);
  });

  it("expresa minutos, horas y días", () => {
    expect(describirRetraso(60)).toBe("1 min después");
    expect(describirRetraso(45 * 60)).toBe("45 min después");
    expect(describirRetraso(2 * 3600)).toBe("2 h después");
    expect(describirRetraso(2 * 3600 + 5 * 60)).toBe("2 h 5 min después");
    expect(describirRetraso(24 * 3600)).toBe("1 día después");
    expect(describirRetraso(3 * 24 * 3600 + 3600)).toBe("3 días después");
    expect(seSincronizoDespues({ retrasoSincronizacionSegundos: 60 })).toBe(true);
  });
});

describe("presentación de estados y tipos", () => {
  it("todo estado tiene etiqueta, explicación y color propios", () => {
    for (const estado of ESTADOS_VALIDACION) {
      expect(etiquetaEstado(estado.valor)).toBe(estado.etiqueta);
      expect(explicacionEstado(estado.valor).length).toBeGreaterThan(20);
      expect(clasesEstado(estado.valor)).toContain("text-");
    }
    expect(ESTADOS_VALIDACION).toHaveLength(5);
  });

  it("distingue lo válido, lo que pide revisión y lo que quedó fuera", () => {
    expect(clasesEstado("VALIDO")).toContain("exito");
    expect(clasesEstado("OBSERVADO")).toContain("advertencia");
    expect(clasesEstado("FUERA_DE_TOLERANCIA")).toContain("peligro");
    expect(clasesEstado("SOSPECHOSO")).toContain("peligro");
  });

  it("traduce los tipos de evento", () => {
    expect(etiquetaTipo("ENTRADA")).toBe("Entrada");
    expect(etiquetaTipo("INICIO_REFRIGERIO")).toBe("Inicio de refrigerio");
    expect(etiquetaTipo("SALIDA")).toBe("Salida");
  });
});

describe("totalPorRevisar", () => {
  it("suma observadas, fuera de tolerancia y sospechosas, y no las válidas ni las sin asignación", () => {
    expect(totalPorRevisar({ VALIDO: 50, OBSERVADO: 2, FUERA_DE_TOLERANCIA: 3, SOSPECHOSO: 1, SIN_ASIGNACION: 9 })).toBe(6);
    expect(totalPorRevisar({})).toBe(0);
  });
});

describe("formatearMetros y urlMapa", () => {
  it("redondea y marca la ausencia de dato", () => {
    expect(formatearMetros(12.5)).toBe("13 m");
    expect(formatearMetros(null)).toBe("—");
  });

  it("arma el enlace al mapa con las coordenadas", () => {
    const url = urlMapa(-12.0464, -77.0428);
    expect(url).toContain("mlat=-12.0464");
    expect(url).toContain("mlon=-77.0428");
    expect(url.startsWith("https://www.openstreetmap.org/")).toBe(true);
  });
});
