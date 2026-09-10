import { useEffect, useState } from "react";
import { servicioAsignaciones } from "../services/servicioAsignaciones";
import { servicioHorarios } from "../services/servicioHorarios";
import { servicioProyectos } from "../services/servicioProyectos";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaAsignacion, RespuestaHorario, RespuestaProyecto, RespuestaUsuario } from "../types/api";

interface Tarjeta {
  etiqueta: string;
  valor: number;
  nota: string;
  icono: string;
}

/** Resumen general: solo métricas reales de lo que ya existe en el sistema (sin cifras inventadas). */
export function PaginaDashboard() {
  const { token, perfil } = useSesion();
  const [usuarios, setUsuarios] = useState<RespuestaUsuario[]>([]);
  const [proyectos, setProyectos] = useState<RespuestaProyecto[]>([]);
  const [horarios, setHorarios] = useState<RespuestaHorario[]>([]);
  const [asignaciones, setAsignaciones] = useState<RespuestaAsignacion[]>([]);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    if (!token) return;
    Promise.all([
      servicioUsuarios.listar(token),
      servicioProyectos.listar(token),
      servicioHorarios.listar(token),
      servicioAsignaciones.listar(token),
    ])
      .then(([u, p, h, a]) => {
        setUsuarios(u);
        setProyectos(p);
        setHorarios(h);
        setAsignaciones(a);
      })
      .finally(() => setCargando(false));
  }, [token]);

  const tecnicos = usuarios.filter((u) => u.rol === "COLABORADOR");
  const activos = usuarios.filter((u) => u.activo);
  const asignacionesVigentes = asignaciones.filter((a) => a.estado === "VIGENTE");

  const tarjetas: Tarjeta[] = [
    { etiqueta: "Usuarios totales", valor: usuarios.length, nota: `${activos.length} activos`, icono: "👥" },
    { etiqueta: "Técnicos", valor: tecnicos.length, nota: "Rol COLABORADOR", icono: "🧑‍🔧" },
    { etiqueta: "Sedes y servicios", valor: proyectos.length, nota: "Proyectos registrados", icono: "📍" },
    { etiqueta: "Turnos configurados", valor: horarios.length, nota: "Horarios activos", icono: "⏰" },
    { etiqueta: "Asignaciones vigentes", valor: asignacionesVigentes.length, nota: `${asignaciones.length} en total`, icono: "🗂️" },
  ];

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center justify-between shrink-0">
        <h1 className="text-lg font-bold text-texto">Dashboard</h1>
        <p className="text-sm text-textoSuave">
          Hola, <span className="font-semibold text-texto">{perfil?.nombres}</span>
        </p>
      </header>

      <main className="flex-1 p-6 overflow-y-auto">
        {cargando ? (
          <p className="text-sm text-textoSuave">Cargando…</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {tarjetas.map((tarjeta) => (
              <div key={tarjeta.etiqueta} className="bg-superficie rounded-md shadow-tarjeta p-5 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <p className="text-xs font-semibold tracking-wide text-textoSuave uppercase">{tarjeta.etiqueta}</p>
                  <span className="w-9 h-9 rounded-sm bg-primario/10 text-primario flex items-center justify-center text-base" aria-hidden="true">
                    {tarjeta.icono}
                  </span>
                </div>
                <p className="text-3xl font-bold text-texto">{tarjeta.valor}</p>
                <p className="text-xs text-textoSuave">{tarjeta.nota}</p>
              </div>
            ))}
          </div>
        )}
      </main>
    </>
  );
}
