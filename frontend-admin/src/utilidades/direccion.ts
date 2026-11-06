/**
 * Dirección aproximada de un punto (geocodificación inversa). La consulta la hace nuestro servidor con el servicio
 * público de OpenStreetMap (Nominatim); aquí solo se da formato a su respuesta. Es una ayuda para entender dónde
 * cae el punto: no se guarda ni reemplaza a las coordenadas.
 */

export interface RespuestaNominatim {
  display_name?: string;
  address?: Record<string, string | undefined>;
  error?: string;
}

/** «Av. El Olivar 123, Urb. Los Pinos, Los Olivos»: calle y número, zona y distrito, sin repetir partes. */
export function formatearDireccion(respuesta: RespuestaNominatim | null | undefined): string | null {
  if (!respuesta || respuesta.error) return null;
  const a = respuesta.address ?? {};
  const via = a.road ?? a.pedestrian ?? a.footway ?? a.path;
  const calle = via ? (a.house_number ? `${via} ${a.house_number}` : via) : undefined;
  const zona = a.suburb ?? a.neighbourhood ?? a.quarter;
  const distrito = a.city_district ?? a.city ?? a.town ?? a.village ?? a.municipality;

  const partes: string[] = [];
  for (const parte of [calle, zona, distrito]) {
    if (parte && !partes.includes(parte)) partes.push(parte);
  }
  if (partes.length > 0) return partes.join(", ");

  if (respuesta.display_name) {
    return respuesta.display_name
      .split(",")
      .slice(0, 3)
      .map((p) => p.trim())
      .join(", ");
  }
  return null;
}

/** Clave de caché: 4 decimales (≈ 11 m) bastan para no repetir la consulta de un punto que apenas se movió. */
export function claveDePunto(latitud: number, longitud: number): string {
  return `${latitud.toFixed(4)},${longitud.toFixed(4)}`;
}
