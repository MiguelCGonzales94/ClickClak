import type { SVGProps } from "react";

export type NombreIcono =
  | "agenda"
  | "biometria"
  | "cerrar"
  | "entrada"
  | "estado"
  | "pin"
  | "salida"
  | "seguridad"
  | "sinConexion"
  | "usuario";

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
  biometria: (
    <>
      <path d="M7.5 12.8v-1.9a4.5 4.5 0 0 1 9 0v1.2" />
      <path d="M9.5 15.5v-4.4a2.5 2.5 0 0 1 5 0v1.6" />
      <path d="M12 18.8v-7.6" />
      <path d="M15.8 16.2c.9-1 1.4-2.5 1.4-4.3" />
      <path d="M8.2 16.8c-.9-1.2-1.4-2.8-1.4-4.9" />
    </>
  ),
  cerrar: (
    <>
      <path d="M10 5H6.8A1.8 1.8 0 0 0 5 6.8v10.4A1.8 1.8 0 0 0 6.8 19H10" />
      <path d="M14 8l4 4-4 4" />
      <path d="M18 12H9" />
    </>
  ),
  entrada: (
    <>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 7.5v8" />
      <path d="m8.8 10.8 3.2-3.3 3.2 3.3" />
    </>
  ),
  estado: (
    <>
      <path d="M5 12.5 9 16l10-10" />
      <path d="M4 19h16" />
    </>
  ),
  pin: (
    <>
      <path d="M12 21s6-5.2 6-11a6 6 0 0 0-12 0c0 5.8 6 11 6 11Z" />
      <circle cx="12" cy="10" r="2.2" />
    </>
  ),
  salida: (
    <>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 16.5v-8" />
      <path d="m8.8 13.2 3.2 3.3 3.2-3.3" />
    </>
  ),
  seguridad: (
    <>
      <path d="M12 3.8 18 6v4.8c0 4-2.4 7.2-6 8.9-3.6-1.7-6-4.9-6-8.9V6l6-2.2Z" />
      <path d="m9.5 12 1.8 1.8 3.7-4" />
    </>
  ),
  sinConexion: (
    <>
      <path d="M5 9.6a10.4 10.4 0 0 1 14 0" />
      <path d="M8.2 12.8a5.8 5.8 0 0 1 7.6 0" />
      <path d="M12 17h.01" />
      <path d="m4.5 4.5 15 15" />
    </>
  ),
  usuario: (
    <>
      <circle cx="12" cy="8.5" r="3.2" />
      <path d="M5.5 19a6.5 6.5 0 0 1 13 0" />
    </>
  ),
};
