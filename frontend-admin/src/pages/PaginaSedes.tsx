import { useCallback, useEffect, useState } from "react";
import { Modal } from "../components/Modal";
import { EstadoPill, Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioProyectos } from "../services/servicioProyectos";
import { servicioUbicaciones } from "../services/servicioUbicaciones";
import { useSesion } from "../store/ContextoSesion";
import { FormularioSede } from "../sedes/FormularioSede";
import { formatearCoordenadas } from "../sedes/reglas";
import { FormularioServicio } from "../servicios/FormularioServicio";
import type { RespuestaProyecto, RespuestaUbicacion } from "../types/api";
import { MenuAcciones, type OpcionMenu } from "../usuarios/MenuAcciones";

type Dialogo =
  | { tipo: "crearServicio" }
  | { tipo: "editarServicio"; proyecto: RespuestaProyecto }
  | { tipo: "desactivarServicio"; proyecto: RespuestaProyecto }
  | { tipo: "eliminarServicio"; proyecto: RespuestaProyecto }
  | { tipo: "crearSede" }
  | { tipo: "editarSede"; sede: RespuestaUbicacion }
  | { tipo: "desactivarSede"; sede: RespuestaUbicacion }
  | { tipo: "eliminarSede"; sede: RespuestaUbicacion };

/**
 * HU06: servicios (proyectos) y sedes/zonas autorizadas (ubicaciones) dentro de cada uno. Ambos se
 * registran, corrigen, desactivan (baja lógica, reversible) y eliminan (solo si nunca se usaron).
 */
export function PaginaSedes() {
  const { token } = useSesion();
  const [proyectos, setProyectos] = useState<RespuestaProyecto[]>([]);
  const [ubicaciones, setUbicaciones] = useState<RespuestaUbicacion[]>([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);

  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [ejecutando, setEjecutando] = useState(false);

  const cargarTodo = useCallback(() => {
    if (!token) return;
    setCargando(true);
    setErrorCarga(null);
    Promise.all([servicioProyectos.listar(token), servicioUbicaciones.listar(token)])
      .then(([p, u]) => {
        setProyectos(p);
        setUbicaciones(u);
      })
      .catch((err) => setErrorCarga(err instanceof ErrorHttp ? err.message : "No se pudieron cargar los datos"))
      .finally(() => setCargando(false));
  }, [token]);

  useEffect(cargarTodo, [cargarTodo]);

  function nombreProyecto(proyectoId: number) {
    return proyectos.find((p) => p.id === proyectoId)?.nombre ?? `#${proyectoId}`;
  }

  function sedesDe(proyectoId: number) {
    return ubicaciones.filter((u) => u.proyectoId === proyectoId).length;
  }

  function cerrarDialogo() {
    setDialogo(null);
    setErrorAccion(null);
  }

  function terminar(mensaje: string) {
    cerrarDialogo();
    setAviso(mensaje);
    cargarTodo();
  }

  async function ejecutar(accion: () => Promise<unknown>, mensaje: string, mensajeError: string) {
    setEjecutando(true);
    setErrorAccion(null);
    try {
      await accion();
      terminar(mensaje);
    } catch (err) {
      setErrorAccion(err instanceof ErrorHttp ? err.message : mensajeError);
    } finally {
      setEjecutando(false);
    }
  }

  function opcionesServicio(proyecto: RespuestaProyecto): OpcionMenu[] {
    if (!token) return [];
    const eliminar: OpcionMenu = { etiqueta: "Eliminar", peligro: true, alElegir: () => setDialogo({ tipo: "eliminarServicio", proyecto }) };
    if (!proyecto.activo) {
      return [
        {
          etiqueta: "Reactivar",
          alElegir: () =>
            ejecutar(() => servicioProyectos.reactivar(token, proyecto.id), `Se reactivó ${proyecto.nombre}.`, "No se pudo reactivar el servicio"),
        },
        eliminar,
      ];
    }
    return [
      { etiqueta: "Editar", alElegir: () => setDialogo({ tipo: "editarServicio", proyecto }) },
      { etiqueta: "Desactivar", alElegir: () => setDialogo({ tipo: "desactivarServicio", proyecto }) },
      eliminar,
    ];
  }

  function opcionesSede(sede: RespuestaUbicacion): OpcionMenu[] {
    if (!token) return [];
    const eliminar: OpcionMenu = { etiqueta: "Eliminar", peligro: true, alElegir: () => setDialogo({ tipo: "eliminarSede", sede }) };
    if (!sede.activo) {
      return [
        {
          etiqueta: "Reactivar",
          alElegir: () =>
            ejecutar(() => servicioUbicaciones.reactivar(token, sede.id), `Se reactivó ${sede.nombre}.`, "No se pudo reactivar la sede"),
        },
        eliminar,
      ];
    }
    return [
      { etiqueta: "Editar zona", alElegir: () => setDialogo({ tipo: "editarSede", sede }) },
      { etiqueta: "Desactivar", alElegir: () => setDialogo({ tipo: "desactivarSede", sede }) },
      eliminar,
    ];
  }

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center shrink-0">
        <h1 className="text-lg font-bold text-texto">Sedes y servicios</h1>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-8">
        {aviso && (
          <p
            role="status"
            className="flex items-center justify-between gap-3 rounded-sm border border-exito/30 bg-exitoFondo px-3 py-2 text-sm text-exitoTexto"
          >
            {aviso}
            <button onClick={() => setAviso(null)} aria-label="Cerrar aviso" className="text-lg leading-none">
              ×
            </button>
          </p>
        )}
        {errorCarga && (
          <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
            {errorCarga}
          </p>
        )}
        {errorAccion && dialogo === null && (
          <p role="alert" className="rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
            {errorAccion}
          </p>
        )}

        <section className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold text-textoSuave uppercase tracking-wide">Servicios (proyectos)</h2>
            <button
              onClick={() => setDialogo({ tipo: "crearServicio" })}
              className="h-9 px-4 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro transition-colors"
            >
              + Nuevo servicio
            </button>
          </div>

          <Tabla
            cargando={cargando}
            vacio="No hay servicios registrados todavía."
            columnas={["Nombre", "Cliente", "Inicio", "Fin", "Sedes", "Estado", ""]}
            filas={proyectos.map((p) => [
              p.nombre,
              p.cliente,
              p.fechaInicio,
              p.fechaFin ?? "—",
              String(sedesDe(p.id)),
              <EstadoPill key="estado" activo={p.activo} />,
              <div key="acciones" className="text-right">
                <MenuAcciones etiqueta={`Acciones del servicio ${p.nombre}`} opciones={opcionesServicio(p)} />
              </div>,
            ])}
          />
        </section>

        <section className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold text-textoSuave uppercase tracking-wide">Sedes y zonas autorizadas</h2>
            <button
              onClick={() => setDialogo({ tipo: "crearSede" })}
              disabled={!proyectos.some((p) => p.activo)}
              title={!proyectos.some((p) => p.activo) ? "Registre o reactive primero un servicio" : undefined}
              className="h-9 px-4 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
            >
              + Nueva sede
            </button>
          </div>

          <Tabla
            cargando={cargando}
            vacio="No hay sedes registradas todavía."
            columnas={["Sede", "Servicio", "Centro de la zona", "Radio (m)", "Estado", ""]}
            filas={ubicaciones.map((u) => [
              <div key="sede">
                <p className="font-medium">{u.nombre}</p>
                {u.direccionReferencia && <p className="text-xs text-textoSuave">{u.direccionReferencia}</p>}
              </div>,
              nombreProyecto(u.proyectoId),
              <span key="centro" className="font-mono text-xs">
                {formatearCoordenadas(u.latitud, u.longitud)}
              </span>,
              String(u.radioToleranciaMetros),
              <EstadoPill key="estado" activo={u.activo} />,
              <div key="acciones" className="text-right">
                <MenuAcciones etiqueta={`Acciones de la sede ${u.nombre}`} opciones={opcionesSede(u)} />
              </div>,
            ])}
          />
        </section>
      </main>

      {dialogo?.tipo === "crearServicio" && token && (
        <Modal titulo="Nuevo servicio" ancho="md" onCerrar={cerrarDialogo}>
          <FormularioServicio
            token={token}
            alCancelar={cerrarDialogo}
            alGuardar={(creado) => terminar(`Se registró el servicio ${creado.nombre}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "editarServicio" && token && (
        <Modal titulo={`Editar ${dialogo.proyecto.nombre}`} ancho="md" onCerrar={cerrarDialogo}>
          <FormularioServicio
            token={token}
            proyecto={dialogo.proyecto}
            alCancelar={cerrarDialogo}
            alGuardar={(guardado) => terminar(`Se guardaron los cambios de ${guardado.nombre}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "crearSede" && token && (
        <Modal titulo="Nueva sede" ancho="lg" onCerrar={cerrarDialogo}>
          <FormularioSede
            token={token}
            proyectos={proyectos.filter((p) => p.activo)}
            ubicaciones={ubicaciones}
            alCancelar={cerrarDialogo}
            alGuardar={(creada) => terminar(`Se registró la sede ${creada.nombre}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "editarSede" && token && (
        <Modal titulo={`Editar zona de ${dialogo.sede.nombre}`} ancho="lg" onCerrar={cerrarDialogo}>
          <FormularioSede
            token={token}
            sede={dialogo.sede}
            proyectos={proyectos}
            ubicaciones={ubicaciones}
            alCancelar={cerrarDialogo}
            alGuardar={(guardada) => terminar(`Se guardaron los cambios de ${guardada.nombre}.`)}
          />
        </Modal>
      )}

      {dialogo?.tipo === "desactivarServicio" && token && (
        <Confirmacion
          titulo="Desactivar servicio"
          textoBoton="Desactivar"
          textoEjecutando="Desactivando…"
          ejecutando={ejecutando}
          error={errorAccion}
          alCancelar={cerrarDialogo}
          alConfirmar={() =>
            ejecutar(
              () => servicioProyectos.desactivar(token, dialogo.proyecto.id),
              `Se desactivó ${dialogo.proyecto.nombre}.`,
              "No se pudo desactivar el servicio",
            )
          }
        >
          <strong>{dialogo.proyecto.nombre}</strong> ({dialogo.proyecto.cliente}) dejará de estar disponible para nuevas
          asignaciones y sedes. Lo ya registrado se conserva y puede reactivarlo cuando quiera. Si hay técnicos asignados,
          primero hay que moverlos o quitarlos.
        </Confirmacion>
      )}

      {dialogo?.tipo === "desactivarSede" && token && (
        <Confirmacion
          titulo="Desactivar sede"
          textoBoton="Desactivar"
          textoEjecutando="Desactivando…"
          ejecutando={ejecutando}
          error={errorAccion}
          alCancelar={cerrarDialogo}
          alConfirmar={() =>
            ejecutar(
              () => servicioUbicaciones.desactivar(token, dialogo.sede.id),
              `Se desactivó ${dialogo.sede.nombre}.`,
              "No se pudo desactivar la sede",
            )
          }
        >
          <strong>{dialogo.sede.nombre}</strong> ({nombreProyecto(dialogo.sede.proyectoId)}) dejará de estar disponible
          para nuevas asignaciones. Las marcaciones ya registradas se conservan y puede reactivarla cuando quiera. Si hay
          técnicos asignados, primero hay que moverlos o quitarlos.
        </Confirmacion>
      )}

      {dialogo?.tipo === "eliminarServicio" && token && (
        <Confirmacion
          titulo="Eliminar servicio"
          textoBoton="Eliminar"
          textoEjecutando="Eliminando…"
          ejecutando={ejecutando}
          error={errorAccion}
          alCancelar={cerrarDialogo}
          alConfirmar={() =>
            ejecutar(
              () => servicioProyectos.eliminar(token, dialogo.proyecto.id),
              `Se eliminó el servicio ${dialogo.proyecto.nombre}.`,
              "No se pudo eliminar el servicio",
            )
          }
        >
          Se eliminará <strong>{dialogo.proyecto.nombre}</strong> de forma definitiva. Solo es posible si nunca tuvo sedes ni
          asignaciones; si las tiene, el sistema lo rechazará y habrá que desactivarlo. Esta acción no se puede deshacer.
        </Confirmacion>
      )}

      {dialogo?.tipo === "eliminarSede" && token && (
        <Confirmacion
          titulo="Eliminar sede"
          textoBoton="Eliminar"
          textoEjecutando="Eliminando…"
          ejecutando={ejecutando}
          error={errorAccion}
          alCancelar={cerrarDialogo}
          alConfirmar={() =>
            ejecutar(
              () => servicioUbicaciones.eliminar(token, dialogo.sede.id),
              `Se eliminó la sede ${dialogo.sede.nombre}.`,
              "No se pudo eliminar la sede",
            )
          }
        >
          Se eliminará <strong>{dialogo.sede.nombre}</strong> de forma definitiva. Solo es posible si nunca tuvo
          asignaciones; si las tiene, el sistema lo rechazará y habrá que desactivarla. Esta acción no se puede deshacer.
        </Confirmacion>
      )}
    </>
  );
}

function Confirmacion({
  titulo,
  children,
  textoBoton,
  textoEjecutando,
  ejecutando,
  error,
  alCancelar,
  alConfirmar,
}: {
  titulo: string;
  children: React.ReactNode;
  textoBoton: string;
  textoEjecutando: string;
  ejecutando: boolean;
  error: string | null;
  alCancelar: () => void;
  alConfirmar: () => void;
}) {
  return (
    <Modal titulo={titulo} onCerrar={alCancelar}>
      <p className="text-sm text-texto">{children}</p>
      {error && (
        <p role="alert" className="mt-3 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
          {error}
        </p>
      )}
      <div className="mt-5 flex justify-end gap-3">
        <button onClick={alCancelar} className="h-10 rounded-sm border border-borde px-4 text-sm font-semibold text-texto hover:bg-fondo">
          Cancelar
        </button>
        <button
          disabled={ejecutando}
          onClick={alConfirmar}
          className="h-10 rounded-sm bg-peligro px-4 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-60"
        >
          {ejecutando ? textoEjecutando : textoBoton}
        </button>
      </div>
    </Modal>
  );
}
