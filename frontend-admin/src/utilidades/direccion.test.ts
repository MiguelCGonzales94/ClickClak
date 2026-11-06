import { describe, expect, it } from "vitest";
import { claveDePunto, formatearDireccion } from "./direccion";

describe("formatearDireccion", () => {
  it("arma calle con número, zona y distrito", () => {
    expect(
      formatearDireccion({
        address: { road: "Avenida El Olivar", house_number: "123", suburb: "Urb. Los Pinos", city_district: "Los Olivos", city: "Lima" },
      }),
    ).toBe("Avenida El Olivar 123, Urb. Los Pinos, Los Olivos");
  });

  it("sin número usa solo la calle y no repite partes iguales", () => {
    expect(formatearDireccion({ address: { road: "Calle Alfa", suburb: "Callao", city: "Callao" } })).toBe("Calle Alfa, Callao");
  });

  it("si no hay calle ni zona usa el distrito, y si no hay nada recorta el nombre completo", () => {
    expect(formatearDireccion({ address: { town: "Ventanilla" } })).toBe("Ventanilla");
    expect(formatearDireccion({ display_name: "Parque Zonal, Av. Sur, Lima, Lima Metropolitana, Perú", address: {} })).toBe(
      "Parque Zonal, Av. Sur, Lima",
    );
  });

  it("devuelve null ante un error del servicio o una respuesta vacía", () => {
    expect(formatearDireccion({ error: "Unable to geocode" })).toBeNull();
    expect(formatearDireccion({})).toBeNull();
    expect(formatearDireccion(null)).toBeNull();
  });
});

describe("claveDePunto", () => {
  it("puntos a pocos metros comparten clave", () => {
    expect(claveDePunto(-12.022366, -77.097942)).toBe(claveDePunto(-12.022371, -77.097938));
    expect(claveDePunto(-12.0223, -77.0979)).not.toBe(claveDePunto(-12.0234, -77.0979));
  });
});
