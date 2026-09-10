import type { ReactNode } from "react";

export function Campo({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-sm text-texto">
      <span className="font-semibold">{etiqueta}</span>
      {children}
    </label>
  );
}
