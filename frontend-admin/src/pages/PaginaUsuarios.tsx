import { useCallback, useEffect, useRef, useState } from "react";
import { Modal } from "../components/Modal";
import { Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { useSesion } from "../store/ContextoSesion";
import type { FiltrosUsuarios, RespuestaPagina, RespuestaUsuario } from "../types/api";
import { FormularioUsuario } from "../usuarios/FormularioUsuario";
import { MenuAcciones, type OpcionMenu } from "../usuarios/MenuAcciones";
import { PanelHistorialUsuario } from "../usuarios/PanelHistorialUsuario";
import { PillEstadoCuenta } from "../usuarios/PillEstadoCuenta";
import {
  accionesDisponibles,
  ESTADOS_CUENTA,
  nombreCompleto,
  ROLES,
  TAMANO_PAGINA,
} from "../usuarios/reglas";
import { formatearFechaHora } from "../utilidades/fechas";

type Dialogo =
  | { tipo: "crear" }
  | { tipo: "editar"; usuario: RespuestaUsuario }
  | { tipo: "desactivar"; usuario: RespuestaUsuario }
  | { tipo: "eliminar"; usuario: RespuestaUsuario }
  | { tipo: "restablecer"; usuario: RespuestaUsuario }
  | { tipo: "clave-temporal"; usuario: RespuestaUsuario; clave: string }
  | { tipo: "historial"; usuario: RespuestaUsuario };

const ESPERA_BUSQUEDA_MS = 300;

/** HU04: alta, edición, búsqueda, estado de cuenta, claves, historial y baja o eliminación de usuarios. */
export function PaginaUsuarios() {
  const { token, perfil } = useSesion();
  const [texto, setTexto] = useState("");
  const [filtros, setFiltros] = useState<FiltrosUsuarios>({
    texto: "",
    rol: "",
    estado: "",
    pagina: 0,
    tamano: TAMANO_PAGINA,
  });
  const [resultado, setResultado] = useState<RespuestaPagina<RespuestaUsuario> | null>(null);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const [motivo, setMotivo] = useState("");
  const [ejecutando, setEjecutando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [copiada, setCopiada] = useState(false);
  const peticion = useRef(0);

  // El texto se aplica con una espera corta para no pedir al servidor en cada tecla.
  useEffect(() => {
    const temporizador = setTimeout(() => {
      setFiltros((anteriores) =>
        anteriores.texto === texto ? anteriores : { ...anteriores, texto, pagina: 0 },
      );
    }, ESPERA_BUSQUEDA_MS);
    return () => clearTimeout(temporizador);
  }, [texto]);

  useEffect(() => {
    if (!token) return;
    const numero = ++peticion.current;
    setCargando(true);
    setErrorCarga(null);
    servicioUsuarios
      .buscar(token, filtros)
      .then((datos) => {
        if (numero === peticion.current) setResultado(datos);
      })
      .catch((err) => {
        if (numero === peticion.current) {
          setErrorCarga(err instanceof ErrorHttp ? err.message : "No se pudo cargar la lista de usuarios");
        }
      })
      .finally(() => {
        if (numero === peticion.current) setCargando(false);
      });
  }, [token, filtros, version]);

  const recargar = useCallback(() => setVersion((valor) => valor + 1), []);

  function cerrarDialogo() {
    setDialogo(null);
    setMotivo("");
    setErrorAccion(null);
    setCopiada(false);
  }

  /** Ejecuta una acción del servidor y muestra el error dentro del cuadro abierto, o el aviso al terminar. */
  async function ejecutar(accion: () => Promise<void>, mensajeExito: string) {
    setEjecutando(true);
    setErrorAccion(null);
    try {
      await accion();
      setAviso(mensajeExito);
      recargar();
    } catch (err) {
      setErrorAccion(err instanceof ErrorHttp ? err.message : "No se pudo completar la operación");
      throw err;
    } finally {
      setEjecutando(false);
    }
  }

  async function confirmarDesactivar(usuario: RespuestaUsuario) {
    if (!token) return;
    try {
      await ejecutar(
        async () => void (await servicioUsuarios.desactivar(token, usuario.id, motivo)),
        `Se desactivó a ${nombreCompleto(usuario)}.`,
      );
      cerrarDialogo();
    } catch {
      // El error ya se muestra en el cuadro; no se cierra para que el administrador lo vea.
    }
  }

  async function confirmarEliminar(usuario: RespuestaUsuario) {
    if (!token) return;
    try {
      await ejecutar(() => servicioUsuarios.eliminar(token, usuario.id), `Se eliminó a ${nombreCompleto(usuario)}.`);
      cerrarDialogo();
    } catch {
      // Un 409 (tiene historial) se explica en el cuadro, con la salida: desactivar.
    }
  }

  async function confirmarRestablecer(usuario: RespuestaUsuario) {
    if (!token) return;
    setEjecutando(true);
    setErrorAccion(null);
    try {
      const respuesta = await servicioUsuarios.restablecerClave(token, usuario.id);
      setDialogo({ tipo: "clave-temporal", usuario, clave: respuesta.claveTemporal });
      recargar();
    } catch (err) {
      setErrorAccion(err instanceof ErrorHttp ? err.message : "No se pudo restablecer la contraseña");
    } finally {
      setEjecutando(false);
    }
  }

  async function activar(usuario: RespuestaUsuario) {
    if (!token) return;
    try {
      await servicioUsuarios.activar(token, usuario.id);
      setAviso(`Se activó a ${nombreCompleto(usuario)}.`);
      recargar();
    } catch (err) {
      setAviso(null);
      setErrorCarga(err instanceof ErrorHttp ? err.message : "No se pudo activar el usuario");
    }
  }

  async function desbloquear(usuario: RespuestaUsuario) {
    if (!token) return;
    try {
      await servicioUsuarios.desbloquear(token, usuario.id);
      setAviso(`Se desbloqueó a ${nombreCompleto(usuario)}.`);
      recargar();
    } catch (err) {
      setAviso(null);
      setErrorCarga(err instanceof ErrorHttp ? err.message : "No se pudo desbloquear el usuario");
    }
  }

  async function copiarClave(clave: string) {
    try {
      await navigator.clipboard.writeText(clave);
      setCopiada(true);
    } catch {
      setCopiada(false);
    }
  }

  function opcionesDe(usuario: RespuestaUsuario): OpcionMenu[] {
    const permitidas = accionesDisponibles(usuario, perfil?.id);
    const opciones: OpcionMenu[] = [];
    if (permitidas.editar) opciones.push({ etiqueta: "Editar", alElegir: () => setDialogo({ tipo: "editar", usuario }) });
    if (permitidas.desbloquear) opciones.push({ etiqueta: "Desbloquear cuenta", alElegir: () => desbloquear(usuario) });
    if (permitidas.restablecerClave) {
      opciones.push({ etiqueta: "Restablecer contraseña", alElegir: () => setDialogo({ tipo: "restablecer", usuario }) });
    }
    opciones.push({ etiqueta: "Ver historial", alElegir: () => setDialogo({ tipo: "historial", usuario }) });
    if (permitidas.activar) opciones.push({ etiqueta: "Activar", alElegir: () => activar(usuario) });
    if (permitidas.desactivar) {
      opciones.push({ etiqueta: "Desactivar", peligro: true, alElegir: () => setDialogo({ tipo: "desactivar", usuario }) });
    }
    if (permitidas.eliminar) {
      opciones.push({ etiqueta: "Eliminar", peligro: true, alElegir: () => setDialogo({ tipo: "eliminar", usuario }) });
    }
    return opciones;
  }

  const hayFiltros = texto.trim() !== "" || filtros.rol !== "" || filtros.estado !== "";
  const desde = resultado && resultado.total > 0 ? resultado.pagina * resultado.tamano + 1 : 0;
  const hasta = resultado ? Math.min((resultado.pagina + 1) * resultado.tamano, resultado.total) : 0;

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center justify-between shrink-0">
        <h1 className="text-lg font-bold text-texto">Usuarios</h1>
        <button
          onClick={() => setDialogo({ tipo: "crear" })}
          className="h-9 px-4 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro transition-colors"
        >
          + Nuevo usuario
        </button>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-4">
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

        <div className="flex flex-wrap items-end gap-3">
          <label className="flex min-w-[220px] flex-1 flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Buscar</span>
            <input
              type="search"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="Nombre, correo o documento"
              className="campo"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Rol</span>
            <select
              value={filtros.rol}
              onChange={(e) => setFiltros({ ...filtros, rol: e.target.value, pagina: 0 })}
              className="campo"
            >
              <option value="">Todos</option>
              {ROLES.map((rol) => (
                <option key={rol} value={rol}>
                  {rol}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Estado de cuenta</span>
            <select
              value={filtros.estado}
              onChange={(e) => setFiltros({ ...filtros, estado: e.target.value as FiltrosUsuarios["estado"], pagina: 0 })}
              className="campo"
            >
              <option value="">Todos</option>
              {ESTADOS_CUENTA.map((estado) => (
                <option key={estado.valor} value={estado.valor}>
                  {estado.etiqueta}
                </option>
              ))}
            </select>
          </label>
          {hayFiltros && (
            <button
              onClick={() => {
                setTexto("");
                setFiltros({ ...filtros, texto: "", rol: "", estado: "", pagina: 0 });
              }}
              className="h-11 px-3 text-sm font-semibold text-primario hover:text-primarioOscuro"
            >
              Limpiar filtros
            </button>
          )}
        </div>

        <Tabla
          cargando={cargando && !resultado}
          vacio={hayFiltros ? "Ningún usuario coincide con la búsqueda." : "No hay usuarios registrados todavía."}
          columnas={["Nombre", "Correo", "Rol", "Estado de cuenta", ""]}
          filas={(resultado?.contenido ?? []).map((usuario) => [
            <div key="nombre">
              <p className="font-medium">
                {nombreCompleto(usuario)}
                {usuario.id === perfil?.id && <span className="ml-2 text-xs font-normal text-textoSuave">(usted)</span>}
              </p>
              <p className="text-xs text-textoSuave">
                {usuario.tipoDocumento} {usuario.numeroDocumento}
              </p>
            </div>,
            <span key="correo" className="text-textoSuave">
              {usuario.correo}
            </span>,
            <span key="rol" className="text-textoSuave">
              {usuario.rol}
            </span>,
            <PillEstadoCuenta
              key="estado"
              estado={usuario.estadoCuenta}
              detalle={
                usuario.motivoBaja
                  ? `Motivo: ${usuario.motivoBaja}`
                  : usuario.desactivadoEn
                    ? `Desactivado el ${formatearFechaHora(usuario.desactivadoEn)}`
                    : null
              }
            />,
            <div key="acciones" className="text-right">
              <MenuAcciones etiqueta={`Acciones de ${nombreCompleto(usuario)}`} opciones={opcionesDe(usuario)} />
            </div>,
          ])}
        />

        {resultado && (
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-textoSuave">
            <span>
              {resultado.total === 0
                ? "0 usuarios"
                : `Mostrando ${desde}–${hasta} de ${resultado.total} usuarios`}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={resultado.pagina === 0 || cargando}
                onClick={() => setFiltros({ ...filtros, pagina: filtros.pagina - 1 })}
                className="h-9 rounded-sm border border-borde px-3 font-semibold text-texto hover:bg-fondo disabled:opacity-40"
              >
                Anterior
              </button>
              <span>
                Página {resultado.totalPaginas === 0 ? 0 : resultado.pagina + 1} de {resultado.totalPaginas}
              </span>
              <button
                disabled={resultado.pagina + 1 >= resultado.totalPaginas || cargando}
                onClick={() => setFiltros({ ...filtros, pagina: filtros.pagina + 1 })}
                className="h-9 rounded-sm border border-borde px-3 font-semibold text-texto hover:bg-fondo disabled:opacity-40"
              >
                Siguiente
              </button>
            </div>
          </div>
        )}
      </main>

      {dialogo?.tipo === "crear" && token && (
        <Modal titulo="Nuevo usuario" ancho="md" onCerrar={cerrarDialogo}>
          <FormularioUsuario
            token={token}
            alCancelar={cerrarDialogo}
            alGuardar={(creado) => {
              cerrarDialogo();
              setAviso(`Se creó a ${nombreCompleto(creado)}.`);
              recargar();
            }}
          />
        </Modal>
      )}

      {dialogo?.tipo === "editar" && token && (
        <Modal titulo={`Editar a ${nombreCompleto(dialogo.usuario)}`} ancho="md" onCerrar={cerrarDialogo}>
          <FormularioUsuario
            token={token}
            usuario={dialogo.usuario}
            alCancelar={cerrarDialogo}
            alGuardar={(guardado) => {
              cerrarDialogo();
              setAviso(`Se guardaron los cambios de ${nombreCompleto(guardado)}.`);
              recargar();
            }}
          />
        </Modal>
      )}

      {dialogo?.tipo === "desactivar" && (
        <Modal titulo="Desactivar usuario" onCerrar={cerrarDialogo}>
          <p className="text-sm text-texto">
            {nombreCompleto(dialogo.usuario)} dejará de poder iniciar sesión de inmediato. Su historial se conserva y
            podrá activarlo de nuevo.
          </p>
          <label className="mt-4 flex flex-col gap-1 text-sm text-texto">
            <span className="font-semibold">Motivo (opcional)</span>
            <textarea
              value={motivo}
              maxLength={255}
              rows={3}
              onChange={(e) => setMotivo(e.target.value)}
              placeholder="Por ejemplo: fin de contrato"
              className="rounded-sm border border-borde bg-superficie px-3 py-2 text-sm text-texto focus:outline-none focus:ring-2 focus:ring-primario/25 focus:border-primario"
            />
          </label>
          {errorAccion && (
            <p role="alert" className="mt-3 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
              {errorAccion}
            </p>
          )}
          <div className="mt-5 flex justify-end gap-3">
            <button onClick={cerrarDialogo} className="h-10 rounded-sm border border-borde px-4 text-sm font-semibold text-texto hover:bg-fondo">
              Cancelar
            </button>
            <button
              disabled={ejecutando}
              onClick={() => confirmarDesactivar(dialogo.usuario)}
              className="h-10 rounded-sm bg-peligro px-4 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-60"
            >
              {ejecutando ? "Desactivando…" : "Desactivar"}
            </button>
          </div>
        </Modal>
      )}

      {dialogo?.tipo === "eliminar" && (
        <Modal titulo="Eliminar usuario" onCerrar={cerrarDialogo}>
          <p className="text-sm text-texto">
            Se eliminará a <strong>{nombreCompleto(dialogo.usuario)}</strong> de forma permanente. Solo es posible si
            nunca registró marcaciones, incidencias, asignaciones ni dispositivos; si tiene historial, desactívelo en
            su lugar.
          </p>
          {errorAccion && (
            <p role="alert" className="mt-3 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
              {errorAccion}
            </p>
          )}
          <div className="mt-5 flex justify-end gap-3">
            <button onClick={cerrarDialogo} className="h-10 rounded-sm border border-borde px-4 text-sm font-semibold text-texto hover:bg-fondo">
              Cancelar
            </button>
            <button
              disabled={ejecutando}
              onClick={() => confirmarEliminar(dialogo.usuario)}
              className="h-10 rounded-sm bg-peligro px-4 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-60"
            >
              {ejecutando ? "Eliminando…" : "Eliminar definitivamente"}
            </button>
          </div>
        </Modal>
      )}

      {dialogo?.tipo === "restablecer" && (
        <Modal titulo="Restablecer contraseña" onCerrar={cerrarDialogo}>
          <p className="text-sm text-texto">
            Se asignará una contraseña temporal a <strong>{nombreCompleto(dialogo.usuario)}</strong>. La contraseña
            actual dejará de servir y, al ingresar con la temporal, tendrá que elegir una nueva. También se
            levantará su bloqueo, si lo tiene.
          </p>
          {errorAccion && (
            <p role="alert" className="mt-3 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
              {errorAccion}
            </p>
          )}
          <div className="mt-5 flex justify-end gap-3">
            <button onClick={cerrarDialogo} className="h-10 rounded-sm border border-borde px-4 text-sm font-semibold text-texto hover:bg-fondo">
              Cancelar
            </button>
            <button
              disabled={ejecutando}
              onClick={() => confirmarRestablecer(dialogo.usuario)}
              className="h-10 rounded-sm bg-primario px-4 text-sm font-semibold text-white hover:bg-primarioOscuro disabled:opacity-60"
            >
              {ejecutando ? "Restableciendo…" : "Generar contraseña temporal"}
            </button>
          </div>
        </Modal>
      )}

      {dialogo?.tipo === "clave-temporal" && (
        <Modal titulo="Contraseña temporal generada" onCerrar={cerrarDialogo}>
          <p className="text-sm text-texto">
            Entregue esta contraseña a <strong>{nombreCompleto(dialogo.usuario)}</strong>. Solo se muestra ahora: si se
            pierde, habrá que restablecerla de nuevo.
          </p>
          <div className="mt-4 flex items-center gap-3">
            <code className="flex-1 select-all rounded-sm border border-borde bg-fondo px-3 py-3 text-center font-mono text-lg tracking-widest text-texto">
              {dialogo.clave}
            </code>
            <button
              onClick={() => copiarClave(dialogo.clave)}
              className="h-11 rounded-sm border border-borde px-4 text-sm font-semibold text-texto hover:bg-fondo"
            >
              {copiada ? "Copiada" : "Copiar"}
            </button>
          </div>
          <div className="mt-5 flex justify-end">
            <button
              onClick={cerrarDialogo}
              className="h-10 rounded-sm bg-primario px-4 text-sm font-semibold text-white hover:bg-primarioOscuro"
            >
              Listo
            </button>
          </div>
        </Modal>
      )}

      {dialogo?.tipo === "historial" && token && (
        <Modal titulo={`Historial de ${nombreCompleto(dialogo.usuario)}`} ancho="lg" onCerrar={cerrarDialogo}>
          <PanelHistorialUsuario token={token} usuarioId={dialogo.usuario.id} />
        </Modal>
      )}
    </>
  );
}
