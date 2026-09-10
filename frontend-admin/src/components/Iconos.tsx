import type { SVGProps } from "react";

export type NombreIcono =
  | "agenda"
  | "asignaciones"
  | "buscar"
  | "chevronIzquierda"
  | "dashboard"
  | "edificio"
  | "logout"
  | "pin"
  | "seguridad"
  | "turnos"
  | "usuarios";

interface Props extends SVGProps<SVGSVGElement> {
  nombre: NombreIcono;
}

export function Icono({ nombre, className = "h-5 w-5", ...props }: Props) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      {...props}
    >
      {trazos[nombre]}
    </svg>
  );
}

const trazos: Record<NombreIcono, JSX.Element> = {
  agenda: (
    <>
      <path d="M5 6.5h14" />
      <path d="M8 4.5v4" />
      <path d="M16 4.5v4" />
      <rect x="4" y="5.5" width="16" height="14" rx="2.5" />
      <path d="M8 12h3" />
      <path d="M8 15.5h6" />
    </>
  ),
  asignaciones: (
    <>
      <path d="M8 7h9.5a2 2 0 0 1 2 2v8.5a2 2 0 0 1-2 2H6.5a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2H8l1.4 2.5Z" />
      <path d="M8.5 13.5 11 16l4.8-5" />
    </>
  ),
  buscar: (
    <>
      <circle cx="10.5" cy="10.5" r="5.5" />
      <path d="m15 15 4 4" />
    </>
  ),
  chevronIzquierda: <path d="m15 6-6 6 6 6" />,
  dashboard: (
    <>
      <rect x="4" y="4" width="7" height="7" rx="2" />
      <rect x="13" y="4" width="7" height="10" rx="2" />
      <rect x="4" y="13" width="7" height="7" rx="2" />
      <path d="M14.5 18h4" />
      <path d="M16.5 16v4" />
    </>
  ),
  edificio: (
    <>
      <path d="M5 20V6.5A1.5 1.5 0 0 1 6.5 5h7A1.5 1.5 0 0 1 15 6.5V20" />
      <path d="M15 9h2.5A1.5 1.5 0 0 1 19 10.5V20" />
      <path d="M8 9h3" />
      <path d="M8 12.5h3" />
      <path d="M8 16h3" />
      <path d="M4 20h16" />
    </>
  ),
  logout: (
    <>
      <path d="M10 5H6.8A1.8 1.8 0 0 0 5 6.8v10.4A1.8 1.8 0 0 0 6.8 19H10" />
      <path d="M14 8l4 4-4 4" />
      <path d="M18 12H9" />
    </>
  ),
  pin: (
    <>
      <path d="M12 21s6-5.2 6-11a6 6 0 0 0-12 0c0 5.8 6 11 6 11Z" />
      <circle cx="12" cy="10" r="2.2" />
    </>
  ),
  seguridad: (
    <>
      <path d="M12 3.8 18 6v4.8c0 4-2.4 7.2-6 8.9-3.6-1.7-6-4.9-6-8.9V6l6-2.2Z" />
      <path d="m9.5 12 1.8 1.8 3.7-4" />
    </>
  ),
  turnos: (
    <>
      <circle cx="12" cy="12" r="7.5" />
      <path d="M12 7.8V12l3 2" />
      <path d="M5.8 5.2 4 7" />
      <path d="m20 7-1.8-1.8" />
    </>
  ),
  usuarios: (
    <>
      <circle cx="9" cy="8" r="3" />
      <path d="M3.8 18.5a5.3 5.3 0 0 1 10.4 0" />
      <path d="M15.5 10.2a2.5 2.5 0 1 0-.8-4.9" />
      <path d="M16.2 15.2a4.4 4.4 0 0 1 4 3.3" />
    </>
  ),
};
