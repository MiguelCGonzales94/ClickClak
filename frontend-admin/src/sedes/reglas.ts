import type { RespuestaUbicacion } from "../types/api";

/** Reglas puras de la zonificación de sedes, separadas para probarlas sin renderizar el mapa. */

/** Centro inicial del mapa cuando la sede aún no tiene zona: Lima. */
export const CENTRO_INICIAL = { latitud: -12.0464, longitud: -77.0428 };
export const RADIO_POR_DEFECTO_METROS = 150;
export const RADIO_MAXIMO_METROS = 5000;

export interface Punto {
  latitud: number;
  longitud: number;
}

export function latitudValida(valor: number): boolean {
  return Number.isFinite(valor) && valor >= -90 && valor <= 90;
}

export function longitudValida(valor: number): boolean {
  return Number.isFinite(valor) && valor >= -180 && valor <= 180;
}

/**
 * Interpreta lo que se pega desde Google Maps u otro sitio: «-12.0464, -77.0428», con espacio o punto y
 * coma de separador, o con coma decimal («-12,0464; -77,0428»). Devuelve null si no son dos coordenadas válidas.
 */
const DOS_NUMEROS = /^(-?\d+(?:[.,]\d+)?)\s*[;,\s]\s*(-?\d+(?:[.,]\d+)?)$/;

export function interpretarCoordenadas(texto: string): Punto | null {
  const coincidencia = DOS_NUMEROS.exec(texto.trim());
  if (!coincidencia) return null;

  const latitud = Number(coincidencia[1].replace(",", "."));
  const longitud = Number(coincidencia[2].replace(",", "."));
  if (!latitudValida(latitud) || !longitudValida(longitud)) return null;
  return { latitud, longitud };
}

/** Redondea a 6 decimales (≈ 11 cm), que es más precisión de la que da cualquier GPS. */
export function redondear(valor: number): number {
  return Math.round(valor * 1e6) / 1e6;
}

export function formatearCoordenadas(latitud: number, longitud: number): string {
  return `${latitud.toFixed(6)}, ${longitud.toFixed(6)}`;
}

export interface ValoresSede {
  proyectoId: string;
  nombre: string;
  direccionReferencia: string;
  latitud: string;
  longitud: string;
  radioToleranciaMetros: string;
}

export function valoresVacios(proyectoId = ""): ValoresSede {
  return {
    proyectoId,
    nombre: "",
    direccionReferencia: "",
    latitud: "",
    longitud: "",
    radioToleranciaMetros: String(RADIO_POR_DEFECTO_METROS),
  };
}

export function valoresDeSede(sede: RespuestaUbicacion): ValoresSede {
  return {
    proyectoId: String(sede.proyectoId),
    nombre: sede.nombre,
    direccionReferencia: sede.direccionReferencia ?? "",
    latitud: String(sede.latitud),
    longitud: String(sede.longitud),
    radioToleranciaMetros: String(sede.radioToleranciaMetros),
  };
}

/** El punto del formulario si latitud y longitud son válidas; si no, null (el mapa no dibuja zona). */
export function puntoDeValores(valores: Pick<ValoresSede, "latitud" | "longitud">): Punto | null {
  if (valores.latitud.trim() === "" || valores.longitud.trim() === "") return null;
  const latitud = Number(valores.latitud);
  const longitud = Number(valores.longitud);
  return latitudValida(latitud) && longitudValida(longitud) ? { latitud, longitud } : null;
}

/** Radio en metros si es un entero entre 1 y el máximo del servidor; si no, null. */
export function radioDeValores(texto: string): number | null {
  const radio = Number(texto);
  return Number.isInteger(radio) && radio >= 1 && radio <= RADIO_MAXIMO_METROS ? radio : null;
}

/** Primer problema del formulario, o null si se puede guardar. Antes de esto la zona «marcada» podía quedar sin registrarse. */
export function validarSede(valores: ValoresSede, editando: boolean): string | null {
  if (!editando && valores.proyectoId === "") return "Elige el servicio al que pertenece la sede.";
  if (valores.nombre.trim() === "") return "Escribe el nombre de la sede.";
  if (puntoDeValores(valores) === null) {
    return "Marca la zona en el mapa o escribe una latitud (−90 a 90) y una longitud (−180 a 180) válidas.";
  }
  if (radioDeValores(valores.radioToleranciaMetros) === null) {
    return `El radio debe ser un número entero de metros entre 1 y ${RADIO_MAXIMO_METROS}.`;
  }
  return null;
}

export function hayCambios(sede: RespuestaUbicacion, valores: ValoresSede): boolean {
  const punto = puntoDeValores(valores);
  return (
    valores.nombre.trim() !== sede.nombre ||
    valores.direccionReferencia.trim() !== (sede.direccionReferencia ?? "") ||
    punto === null ||
    redondear(punto.latitud) !== redondear(sede.latitud) ||
    redondear(punto.longitud) !== redondear(sede.longitud) ||
    radioDeValores(valores.radioToleranciaMetros) !== sede.radioToleranciaMetros
  );
}

/** Las demás sedes del mismo servicio, para dibujarlas atenuadas y evitar zonas encimadas por descuido. */
export function sedesVecinas(sedes: RespuestaUbicacion[], proyectoId: string, excluirId?: number): RespuestaUbicacion[] {
  return sedes.filter((s) => String(s.proyectoId) === proyectoId && s.id !== excluirId && s.activo);
}

/** Cuántos metros mide un grado de latitud; sirve para dimensionar un cuadro alrededor de la zona. */
const METROS_POR_GRADO = 111_320;

/**
 * Límites (sur, oeste, norte, este) que encuadran un círculo con un margen, para ajustar el mapa a la zona
 * sin depender de un nivel de zoom fijo.
 */
export function limitesDeZona(punto: Punto, radioMetros: number): [[number, number], [number, number]] {
  const margen = radioMetros * 1.6;
  const dLat = margen / METROS_POR_GRADO;
  const dLng = margen / (METROS_POR_GRADO * Math.max(Math.cos((punto.latitud * Math.PI) / 180), 0.01));
  return [
    [punto.latitud - dLat, punto.longitud - dLng],
    [punto.latitud + dLat, punto.longitud + dLng],
  ];
}
