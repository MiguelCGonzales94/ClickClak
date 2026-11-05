import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { useEffect, useRef } from "react";
import type { RespuestaUbicacion } from "../types/api";
import { CENTRO_INICIAL, limitesDeZona, redondear, type Punto } from "./reglas";

interface Props {
  /** Centro de la zona, o null mientras no se ha marcado. */
  punto: Punto | null;
  radioMetros: number | null;
  /** Otras sedes del servicio, atenuadas, para ubicar la zona respecto a ellas. */
  vecinas?: RespuestaUbicacion[];
  /** Se llama al hacer clic en el mapa o al soltar el marcador arrastrado. */
  alMarcar: (punto: Punto) => void;
}

const TESELAS = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const ZOOM_INICIAL = 13;

const ICONO = L.divIcon({
  className: "pin-zona",
  iconSize: [22, 22],
  iconAnchor: [11, 11],
});

/**
 * Mapa para marcar la zona de una sede: un clic coloca el centro, el marcador se arrastra y el círculo
 * muestra el radio de tolerancia. Es la misma zona que usa el servidor para validar las marcaciones.
 * Leaflet se maneja de forma imperativa; el estado del formulario sigue siendo la única fuente de verdad.
 */
export function MapaZona({ punto, radioMetros, vecinas = [], alMarcar }: Props) {
  const contenedor = useRef<HTMLDivElement>(null);
  const mapa = useRef<L.Map | null>(null);
  const marcador = useRef<L.Marker | null>(null);
  const circulo = useRef<L.Circle | null>(null);
  const capaVecinas = useRef<L.LayerGroup | null>(null);
  const alMarcarActual = useRef(alMarcar);
  const ultimoMarcado = useRef<Punto | null>(null);

  useEffect(() => {
    alMarcarActual.current = alMarcar;
  }, [alMarcar]);

  // Crea el mapa una sola vez y lo destruye al cerrar el diálogo.
  useEffect(() => {
    if (!contenedor.current) return;
    const instancia = L.map(contenedor.current, { zoomControl: true, attributionControl: true }).setView(
      [CENTRO_INICIAL.latitud, CENTRO_INICIAL.longitud],
      ZOOM_INICIAL,
    );
    L.tileLayer(TESELAS, { maxZoom: 19, attribution: "© OpenStreetMap" }).addTo(instancia);
    capaVecinas.current = L.layerGroup().addTo(instancia);

    instancia.on("click", (evento: L.LeafletMouseEvent) => {
      const marcado = { latitud: redondear(evento.latlng.lat), longitud: redondear(evento.latlng.lng) };
      ultimoMarcado.current = marcado;
      alMarcarActual.current(marcado);
    });

    mapa.current = instancia;
    return () => {
      instancia.remove();
      mapa.current = null;
      marcador.current = null;
      circulo.current = null;
      capaVecinas.current = null;
    };
  }, []);

  // Dibuja o mueve el marcador y el círculo según lo que diga el formulario.
  useEffect(() => {
    const instancia = mapa.current;
    if (!instancia) return;

    if (punto === null) {
      marcador.current?.remove();
      circulo.current?.remove();
      marcador.current = null;
      circulo.current = null;
      return;
    }

    const centro: L.LatLngExpression = [punto.latitud, punto.longitud];
    const radio = radioMetros ?? 1;

    if (!marcador.current) {
      const nuevo = L.marker(centro, { icon: ICONO, draggable: true, keyboard: false }).addTo(instancia);
      nuevo.on("drag", () => circulo.current?.setLatLng(nuevo.getLatLng()));
      nuevo.on("dragend", () => {
        const { lat, lng } = nuevo.getLatLng();
        const marcado = { latitud: redondear(lat), longitud: redondear(lng) };
        ultimoMarcado.current = marcado;
        alMarcarActual.current(marcado);
      });
      marcador.current = nuevo;
    } else {
      marcador.current.setLatLng(centro);
    }

    if (!circulo.current) {
      circulo.current = L.circle(centro, {
        radius: radio,
        color: "#1E4FD8",
        weight: 2,
        fillColor: "#1E4FD8",
        fillOpacity: 0.15,
        interactive: false,
      }).addTo(instancia);
    } else {
      circulo.current.setLatLng(centro);
      circulo.current.setRadius(radio);
    }

    // Si el punto vino del teclado o del pegado (no de un clic), encuadra la zona en pantalla.
    const vinoDelMapa =
      ultimoMarcado.current !== null &&
      ultimoMarcado.current.latitud === redondear(punto.latitud) &&
      ultimoMarcado.current.longitud === redondear(punto.longitud);
    if (!vinoDelMapa) {
      instancia.fitBounds(limitesDeZona(punto, radio), { animate: false, maxZoom: 18 });
    }
  }, [punto, radioMetros]);

  // Las demás sedes del servicio, en gris y con su nombre al pasar el cursor.
  useEffect(() => {
    const capa = capaVecinas.current;
    if (!capa) return;
    capa.clearLayers();
    vecinas.forEach((v) => {
      L.circle([v.latitud, v.longitud], {
        radius: v.radioToleranciaMetros,
        color: "#6B7387",
        weight: 1.5,
        dashArray: "4 4",
        fillColor: "#6B7387",
        fillOpacity: 0.08,
      })
        .bindTooltip(v.nombre)
        .addTo(capa);
    });
  }, [vecinas]);

  return (
    <div
      ref={contenedor}
      role="application"
      aria-label="Mapa para marcar la zona de la sede. También puede escribir la latitud y la longitud."
      className="h-72 w-full rounded-sm border border-borde"
    />
  );
}
