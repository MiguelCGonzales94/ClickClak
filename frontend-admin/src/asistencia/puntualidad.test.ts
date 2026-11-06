import { describe, expect, it } from "vitest";
import type { RespuestaAsistencia } from "../types/api";
import {
  clasesPuntualidad,
  contarTardes,
  describirPuntualidadCorta,
  etiquetaPuntualidad,
  explicarPuntualidad,
} from "./puntualidad";

const turno = { horaInicioTurno: "08:00", toleranciaTurnoMinutos: 10 };

describe("etiquetas", () => {
  it("nombra y colorea cada estado", () => {
    expect(etiquetaPuntualidad("TEMPRANO")).toBe("Temprano");
    expect(etiquetaPuntualidad("DENTRO_DE_TOLERANCIA")).toBe("En tolerancia");
    expect(etiquetaPuntualidad("TARDE")).toBe("Tarde");
    expect(clasesPuntualidad("TARDE")).toMatch(/peligro/);
    expect(clasesPuntualidad("DENTRO_DE_TOLERANCIA")).toMatch(/exito/);
    expect(clasesPuntualidad("TEMPRANO")).toMatch(/primario/);
  });
});

describe("describirPuntualidadCorta", () => {
  it("dice los minutos en cada caso", () => {
    expect(describirPuntualidadCorta("TEMPRANO", -15)).toBe("15 min antes");
    expect(describirPuntualidadCorta("DENTRO_DE_TOLERANCIA", 0)).toBe("A la hora");
    expect(describirPuntualidadCorta("DENTRO_DE_TOLERANCIA", 7)).toBe("+7 min");
    expect(describirPuntualidadCorta("TARDE", 11)).toBe("11 min tarde");
  });
});

describe("explicarPuntualidad", () => {
  it("explica un temprano con el turno y su tolerancia", () => {
    expect(explicarPuntualidad({ puntualidad: "TEMPRANO", minutosRespectoAlInicio: -1, ...turno })).toBe(
      "El turno empieza a las 08:00 y tiene 10 minutos de tolerancia. Llegó 1 minuto antes.",
    );
  });

  it("distingue llegar a la hora de llegar dentro de la tolerancia", () => {
    expect(explicarPuntualidad({ puntualidad: "DENTRO_DE_TOLERANCIA", minutosRespectoAlInicio: 0, ...turno })).toMatch(/a la hora/);
    expect(explicarPuntualidad({ puntualidad: "DENTRO_DE_TOLERANCIA", minutosRespectoAlInicio: 6, ...turno })).toMatch(
      /6 minutos después de la hora, dentro de la tolerancia/,
    );
  });

  it("avisa que la tardanza cuenta", () => {
    expect(explicarPuntualidad({ puntualidad: "TARDE", minutosRespectoAlInicio: 25, ...turno })).toMatch(/cuenta como tardanza/);
  });

  it("no inventa nada cuando no hay turno o no es una entrada", () => {
    expect(
      explicarPuntualidad({ puntualidad: null, minutosRespectoAlInicio: null, horaInicioTurno: null, toleranciaTurnoMinutos: null }),
    ).toBeNull();
  });
});

describe("contarTardes", () => {
  it("cuenta solo las entradas tardías", () => {
    const m = (puntualidad: RespuestaAsistencia["puntualidad"]) => ({ puntualidad }) as RespuestaAsistencia;
    expect(contarTardes([m("TARDE"), m("TEMPRANO"), m(null), m("TARDE")])).toBe(2);
  });
});
