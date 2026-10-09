import type { EstadoCuenta, RespuestaHistorialUsuario, RespuestaUsuario } from "../types/api";

/** Reglas puras de la pantalla de usuarios, separadas para poder probarlas sin renderizar. */

export const ROLES = ["COLABORADOR", "SUPERVISOR", "RRHH_ADMIN"] as const;

export const ESTADOS_CUENTA: { valor: EstadoCuenta; etiqueta: string }[] = [
  { valor: "ACTIVA", etiqueta: "Activa" },
  { valor: "INACTIVA", etiqueta: "Inactiva" },
  { valor: "BLOQUEADA", etiqueta: "Bloqueada" },
  { valor: "CLAVE_PENDIENTE", etiqueta: "Clave pendiente" },
];

export const TIPOS_DOCUMENTO = [
  { valor: "DNI", etiqueta: "DNI" },
  { valor: "CE", etiqueta: "Carné de extranjería" },
  { valor: "PASAPORTE", etiqueta: "Pasaporte" },
];

export const TAMANO_PAGINA = 20;

export function etiquetaEstado(estado: EstadoCuenta): string {
  return ESTADOS_CUENTA.find((e) => e.valor === estado)?.etiqueta ?? estado;
}

/** Colores del pill según el estado, con los tokens ya definidos en Tailwind. */
export function clasesEstado(estado: EstadoCuenta): string {
  switch (estado) {
    case "ACTIVA":
      return "bg-exitoFondo text-exitoTexto";
    case "INACTIVA":
      return "bg-peligroFondo text-peligroTexto";
    case "BLOQUEADA":
    case "CLAVE_PENDIENTE":
      return "bg-advertenciaFondo text-advertenciaTexto";
  }
}

export function esColaborador(rol: string): boolean {
  return rol === "COLABORADOR";
}

/**
 * Devuelve el mensaje de error de un documento, o null si es válido. El DNI peruano tiene 8
 * dígitos; para los demás tipos solo se exige alfanumérico, de 6 a 20 caracteres (el límite del
 * backend), porque no se verificó un formato oficial más estricto.
 */
export function validarDocumento(tipo: string, numero: string): string | null {
  const valor = numero.trim();
  if (!valor) return "Ingrese el número de documento";
  if (tipo === "DNI") {
    return /^\d{8}$/.test(valor) ? null : "El DNI debe tener 8 dígitos";
  }
  return /^[A-Za-z0-9]{6,20}$/.test(valor) ? null : "Use de 6 a 20 letras o números, sin espacios";
}

export type CambioDeRol = "sin-cambio" | "exige-clave" | "borra-clave";

/** Qué implica para la contraseña pasar de un rol a otro (decisión D2 del plan). */
export function cambioDeRol(rolAnterior: string, rolNuevo: string): CambioDeRol {
  const eraColaborador = esColaborador(rolAnterior);
  const seraColaborador = esColaborador(rolNuevo);
  if (eraColaborador === seraColaborador) return "sin-cambio";
  return eraColaborador ? "exige-clave" : "borra-clave";
}

export function nombreCompleto(usuario: Pick<RespuestaUsuario, "nombres" | "apellidos">): string {
  return `${usuario.nombres} ${usuario.apellidos}`.trim();
}

/** Acciones que el menú de una fila ofrece, según el estado de la cuenta y de quién la mira. */
export interface AccionesDisponibles {
  editar: boolean;
  activar: boolean;
  desactivar: boolean;
  desbloquear: boolean;
  restablecerClave: boolean;
  eliminar: boolean;
}

export function accionesDisponibles(usuario: RespuestaUsuario, idPropio: number | undefined): AccionesDisponibles {
  const esPropio = usuario.id === idPropio;
  const inactivo = usuario.estadoCuenta === "INACTIVA";
  return {
    editar: true,
    activar: inactivo,
    // Nadie se da de baja ni se elimina a sí mismo (el servidor también lo impide).
    desactivar: !inactivo && !esPropio,
    desbloquear: usuario.estadoCuenta === "BLOQUEADA",
    restablecerClave: !inactivo && !esPropio && !esColaborador(usuario.rol),
    eliminar: !esPropio,
  };
}

const ETIQUETAS_CAMPO: Record<string, string> = {
  nombres: "Nombres",
  apellidos: "Apellidos",
  tipoDocumento: "Tipo de documento",
  numeroDocumento: "Número de documento",
  correo: "Correo",
  rol: "Rol",
  activo: "Activo",
  motivoBaja: "Motivo de baja",
  debeCambiarClave: "Clave pendiente de cambio",
  clave: "Contraseña",
  bloqueada: "Bloqueada",
};

function formatearValor(valor: unknown): string {
  if (valor === true) return "sí";
  if (valor === false) return "no";
  if (valor === null || valor === undefined) return "—";
  return String(valor);
}

/** Resume una entrada de la bitácora en una frase corta para la línea de tiempo. */
export function describirEntradaHistorial(entrada: RespuestaHistorialUsuario): string {
  if (entrada.accion === "CREACION") return "Cuenta creada";
  if (entrada.accion === "ELIMINACION") return "Cuenta eliminada";

  const anteriores = entrada.valoresAnteriores ?? {};
  const nuevos = entrada.valoresNuevos ?? {};
  const cambios = Object.keys(nuevos).filter((campo) => {
    return JSON.stringify(anteriores[campo]) !== JSON.stringify(nuevos[campo]);
  });
  if (cambios.length === 0) return "Sin cambios registrados";

  return cambios
    .map((campo) => {
      const etiqueta = ETIQUETAS_CAMPO[campo] ?? campo;
      // La bitácora nunca guarda claves: el campo "clave" trae un texto descriptivo, no un valor.
      if (campo === "clave") return `${etiqueta}: ${formatearValor(nuevos[campo])}`;
      return `${etiqueta}: ${formatearValor(anteriores[campo])} → ${formatearValor(nuevos[campo])}`;
    })
    .join(" · ");
}

/**
 * Misma política que el servidor (`PoliticaContrasenas`): al menos 8 caracteres, con letras y
 * números. Se repite aquí solo para avisar antes de enviar; el servidor sigue siendo quien decide.
 */
export function validarClaveNueva(claveActual: string, claveNueva: string, confirmacion: string): string | null {
  if (claveNueva.length < 8) return "La contraseña nueva debe tener al menos 8 caracteres";
  if (!/[A-Za-z]/.test(claveNueva) || !/\d/.test(claveNueva)) return "La contraseña nueva debe combinar letras y números";
  if (claveNueva === claveActual) return "La contraseña nueva debe ser distinta de la actual";
  if (claveNueva !== confirmacion) return "La confirmación no coincide con la contraseña nueva";
  return null;
}
