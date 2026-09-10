import { useEffect, useState } from "react";
import { servicioAsignaciones } from "../services/servicioAsignaciones";
import { servicioHorarios } from "../services/servicioHorarios";
import { servicioProyectos } from "../services/servicioProyectos";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { useSesion } from "../store/ContextoSesion";
import { Icono, type NombreIcono } from "../components/Iconos";
import type { RespuestaAsignacion, RespuestaHorario, RespuestaProyecto, RespuestaUsuario } from "../types/api";

interface Tarjeta {
  etiqueta: string;
  valor: number;
  nota: string;
  icono: NombreIcono;
  tono: string;
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
    {
      etiqueta: "Usuarios totales",
      valor: usuarios.length,
      nota: `${activos.length} activos`,
      icono: "usuarios",
      tono: "text-[#3150D4] bg-[#EEF3FF]",
    },
    {
      etiqueta: "Técnicos",
      valor: tecnicos.length,
      nota: "Rol COLABORADOR",
      icono: "seguridad",
      tono: "text-[#0E9F8F] bg-[#EAFBF7]",
    },
    {
      etiqueta: "Sedes y servicios",
      valor: proyectos.length,
      nota: "Proyectos registrados",
      icono: "pin",
      tono: "text-[#C46A1B] bg-[#FFF3E8]",
    },
    {
      etiqueta: "Turnos configurados",
      valor: horarios.length,
      nota: "Horarios activos",
      icono: "turnos",
      tono: "text-[#7A54C7] bg-[#F2EDFF]",
    },
    {
      etiqueta: "Asignaciones vigentes",
      valor: asignacionesVigentes.length,
      nota: `${asignaciones.length} en total`,
      icono: "asignaciones",
      tono: "text-[#1B6B9A] bg-[#EAF7FF]",
    },
  ];

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-8 flex items-center justify-between shrink-0">
        <h1 className="text-base font-semibold text-texto">Dashboard</h1>
        <p className="text-sm text-textoSuave">
          Hola, <span className="font-semibold text-texto">{perfil?.nombres}</span>
        </p>
      </header>

      <main className="flex-1 p-8 overflow-y-auto bg-[#F3F5F9]">
        {cargando ? (
          <p className="text-sm text-textoSuave">Cargando…</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6 max-w-6xl">
            {tarjetas.map((tarjeta) => (
              <article
                key={tarjeta.etiqueta}
                className="min-h-[142px] bg-superficie rounded-sm border border-[#DEE5F0] shadow-[0_10px_26px_rgba(20,30,70,0.06)] p-5 flex flex-col justify-between"
              >
                <div className="flex items-start justify-between gap-4">
                  <p className="text-[12px] font-bold tracking-wide text-[#66718A] uppercase">
                    {tarjeta.etiqueta}
                  </p>
                  <span className={`h-10 w-10 rounded-sm flex items-center justify-center ${tarjeta.tono}`}>
                    <Icono nombre={tarjeta.icono} className="h-5 w-5" />
                  </span>
                </div>
                <div>
                  <p className="text-[32px] leading-none font-bold text-[#061229]">{tarjeta.valor}</p>
                  <p className="mt-3 text-sm text-[#66718A]">{tarjeta.nota}</p>
                </div>
              </article>
            ))}
          </div>
        )}
      </main>
    </>
  );
}
