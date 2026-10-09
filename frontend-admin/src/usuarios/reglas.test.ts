import { describe, expect, it } from "vitest";
import type { RespuestaHistorialUsuario, RespuestaUsuario } from "../types/api";
import {
  accionesDisponibles,
  cambioDeRol,
  clasesEstado,
  describirEntradaHistorial,
  esColaborador,
  etiquetaEstado,
  nombreCompleto,
  validarClaveNueva,
  validarDocumento,
} from "./reglas";

function usuario(parcial: Partial<RespuestaUsuario>): RespuestaUsuario {
  return {
    id: 10,
    nombres: "Ana",
    apellidos: "Pérez",
    tipoDocumento: "DNI",
    numeroDocumento: "12345678",
    correo: "ana@example.com",
    rol: "SUPERVISOR",
    activo: true,
    estadoCuenta: "ACTIVA",
    desactivadoEn: null,
    motivoBaja: null,
    creadoEn: "2026-10-01T10:00:00Z",
    ...parcial,
  };
}

function entrada(parcial: Partial<RespuestaHistorialUsuario>): RespuestaHistorialUsuario {
  return {
    id: 1,
    accion: "MODIFICACION",
    actorId: 1,
    actorNombre: "Admin Demo",
    valoresAnteriores: null,
    valoresNuevos: null,
    creadoEn: "2026-10-02T10:00:00Z",
    ...parcial,
  };
}

describe("validarDocumento", () => {
  it("exige 8 dígitos para el DNI", () => {
    expect(validarDocumento("DNI", "12345678")).toBeNull();
    expect(validarDocumento("DNI", "1234567")).not.toBeNull();
    expect(validarDocumento("DNI", "123456789")).not.toBeNull();
    expect(validarDocumento("DNI", "1234567a")).not.toBeNull();
  });

  it("ignora espacios alrededor", () => {
    expect(validarDocumento("DNI", " 12345678 ")).toBeNull();
  });

  it("rechaza un número vacío en cualquier tipo", () => {
    expect(validarDocumento("DNI", "")).toBe("Ingrese el número de documento");
    expect(validarDocumento("CE", "   ")).toBe("Ingrese el número de documento");
  });

  it("acepta alfanuméricos de 6 a 20 para carné de extranjería y pasaporte", () => {
    expect(validarDocumento("CE", "A1B2C3")).toBeNull();
    expect(validarDocumento("PASAPORTE", "X".repeat(20))).toBeNull();
    expect(validarDocumento("PASAPORTE", "12345")).not.toBeNull();
    expect(validarDocumento("PASAPORTE", "X".repeat(21))).not.toBeNull();
    expect(validarDocumento("CE", "AB 123 456")).not.toBeNull();
  });
});

describe("cambioDeRol", () => {
  it("exige contraseña al pasar de colaborador a un rol con acceso por clave", () => {
    expect(cambioDeRol("COLABORADOR", "SUPERVISOR")).toBe("exige-clave");
    expect(cambioDeRol("COLABORADOR", "RRHH_ADMIN")).toBe("exige-clave");
  });

  it("borra la contraseña al pasar a colaborador", () => {
    expect(cambioDeRol("SUPERVISOR", "COLABORADOR")).toBe("borra-clave");
    expect(cambioDeRol("RRHH_ADMIN", "COLABORADOR")).toBe("borra-clave");
  });

  it("no toca la contraseña si el tipo de acceso no cambia", () => {
    expect(cambioDeRol("SUPERVISOR", "RRHH_ADMIN")).toBe("sin-cambio");
    expect(cambioDeRol("COLABORADOR", "COLABORADOR")).toBe("sin-cambio");
  });
});

describe("accionesDisponibles", () => {
  it("ofrece desactivar y restablecer clave a un supervisor activo ajeno", () => {
    const acciones = accionesDisponibles(usuario({}), 99);

    expect(acciones).toEqual({
      editar: true,
      activar: false,
      desactivar: true,
      desbloquear: false,
      restablecerClave: true,
      eliminar: true,
    });
  });

  it("para un inactivo ofrece activar y no desactivar ni restablecer", () => {
    const acciones = accionesDisponibles(usuario({ activo: false, estadoCuenta: "INACTIVA" }), 99);

    expect(acciones.activar).toBe(true);
    expect(acciones.desactivar).toBe(false);
    expect(acciones.restablecerClave).toBe(false);
  });

  it("ofrece desbloquear solo si la cuenta está bloqueada", () => {
    expect(accionesDisponibles(usuario({ estadoCuenta: "BLOQUEADA" }), 99).desbloquear).toBe(true);
    expect(accionesDisponibles(usuario({ estadoCuenta: "CLAVE_PENDIENTE" }), 99).desbloquear).toBe(false);
  });

  it("no ofrece restablecer clave a un colaborador, que no tiene contraseña", () => {
    expect(accionesDisponibles(usuario({ rol: "COLABORADOR" }), 99).restablecerClave).toBe(false);
  });

  it("sobre la propia cuenta no deja desactivar, restablecer ni eliminar", () => {
    const acciones = accionesDisponibles(usuario({ id: 10, rol: "RRHH_ADMIN" }), 10);

    expect(acciones.desactivar).toBe(false);
    expect(acciones.restablecerClave).toBe(false);
    expect(acciones.eliminar).toBe(false);
    expect(acciones.editar).toBe(true);
  });
});

describe("describirEntradaHistorial", () => {
  it("nombra la creación y la eliminación", () => {
    expect(describirEntradaHistorial(entrada({ accion: "CREACION" }))).toBe("Cuenta creada");
    expect(describirEntradaHistorial(entrada({ accion: "ELIMINACION" }))).toBe("Cuenta eliminada");
  });

  it("muestra solo los campos que cambiaron, con valores legibles", () => {
    const texto = describirEntradaHistorial(
      entrada({
        valoresAnteriores: { nombres: "Ana", activo: true, rol: "SUPERVISOR" },
        valoresNuevos: { nombres: "Ana", activo: false, rol: "SUPERVISOR", motivoBaja: "Fin de contrato" },
      }),
    );

    expect(texto).toContain("Activo: sí → no");
    expect(texto).toContain("Motivo de baja: — → Fin de contrato");
    expect(texto).not.toContain("Nombres");
    expect(texto).not.toContain("Rol");
  });

  it("para un restablecimiento de clave no muestra ningún valor de contraseña", () => {
    const texto = describirEntradaHistorial(
      entrada({
        valoresAnteriores: { debeCambiarClave: false },
        valoresNuevos: { debeCambiarClave: true, clave: "restablecida por el administrador" },
      }),
    );

    expect(texto).toContain("Contraseña: restablecida por el administrador");
    expect(texto).toContain("Clave pendiente de cambio: no → sí");
  });

  it("avisa cuando no hay cambios", () => {
    expect(describirEntradaHistorial(entrada({ valoresAnteriores: { a: 1 }, valoresNuevos: { a: 1 } }))).toBe(
      "Sin cambios registrados",
    );
  });
});

describe("utilidades de presentación", () => {
  it("traduce el estado a su etiqueta", () => {
    expect(etiquetaEstado("CLAVE_PENDIENTE")).toBe("Clave pendiente");
    expect(etiquetaEstado("ACTIVA")).toBe("Activa");
  });

  it("asigna colores distintos a activa, inactiva y estados de atención", () => {
    expect(clasesEstado("ACTIVA")).toContain("exito");
    expect(clasesEstado("INACTIVA")).toContain("peligro");
    expect(clasesEstado("BLOQUEADA")).toContain("advertencia");
    expect(clasesEstado("CLAVE_PENDIENTE")).toContain("advertencia");
  });

  it("reconoce al colaborador y arma el nombre completo", () => {
    expect(esColaborador("COLABORADOR")).toBe(true);
    expect(esColaborador("SUPERVISOR")).toBe(false);
    expect(nombreCompleto({ nombres: "Ana", apellidos: "Pérez" })).toBe("Ana Pérez");
  });
});

describe("validarClaveNueva", () => {
  it("acepta una clave de 8 o más caracteres con letras y números", () => {
    expect(validarClaveNueva("Actual12345", "Nueva12345", "Nueva12345")).toBeNull();
  });

  it("rechaza claves cortas o sin mezcla de letras y números", () => {
    expect(validarClaveNueva("Actual12345", "Ab1", "Ab1")).toContain("al menos 8");
    expect(validarClaveNueva("Actual12345", "soloLetrasAqui", "soloLetrasAqui")).toContain("letras y números");
    expect(validarClaveNueva("Actual12345", "123456789", "123456789")).toContain("letras y números");
  });

  it("rechaza una clave igual a la actual", () => {
    expect(validarClaveNueva("Misma12345", "Misma12345", "Misma12345")).toContain("distinta");
  });

  it("rechaza una confirmación que no coincide", () => {
    expect(validarClaveNueva("Actual12345", "Nueva12345", "Nueva12346")).toContain("no coincide");
  });
});
