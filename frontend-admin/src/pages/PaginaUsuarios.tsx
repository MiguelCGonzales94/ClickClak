import { useEffect, useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { EstadoPill, Tabla } from "../components/Tabla";
import { ErrorHttp } from "../services/clienteApi";
import { servicioUsuarios } from "../services/servicioUsuarios";
import { useSesion } from "../store/ContextoSesion";
import type { RespuestaUsuario } from "../types/api";

const ROLES = ["COLABORADOR", "SUPERVISOR", "RRHH_ADMIN"];

const FORMULARIO_VACIO = {
  nombres: "",
  apellidos: "",
  tipoDocumento: "DNI",
  numeroDocumento: "",
  correo: "",
  rol: "COLABORADOR",
  password: "",
};

/** HU04: alta, listado y activar/desactivar usuarios. */
export function PaginaUsuarios() {
  const { token } = useSesion();
  const [usuarios, setUsuarios] = useState<RespuestaUsuario[]>([]);
  const [cargando, setCargando] = useState(true);
  const [mostrarFormulario, setMostrarFormulario] = useState(false);
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargarUsuarios() {
    if (!token) return;
    setCargando(true);
    servicioUsuarios
      .listar(token)
      .then(setUsuarios)
      .finally(() => setCargando(false));
  }

  useEffect(cargarUsuarios, [token]);

  async function manejarCrear(evento: FormEvent) {
    evento.preventDefault();
    if (!token) return;
    setError(null);
    setGuardando(true);
    try {
      await servicioUsuarios.registrar(token, {
        ...formulario,
        password: formulario.rol === "COLABORADOR" ? undefined : formulario.password,
      });
      setFormulario(FORMULARIO_VACIO);
      setMostrarFormulario(false);
      cargarUsuarios();
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo guardar el usuario");
    } finally {
      setGuardando(false);
    }
  }

  async function manejarCambiarEstado(usuario: RespuestaUsuario) {
    if (!token) return;
    const accion = usuario.activo ? servicioUsuarios.desactivar : servicioUsuarios.activar;
    await accion(token, usuario.id);
    cargarUsuarios();
  }

  return (
    <>
      <header className="h-16 bg-superficie border-b border-borde px-6 flex items-center justify-between shrink-0">
        <h1 className="text-lg font-bold text-texto">Usuarios</h1>
        <button
          onClick={() => setMostrarFormulario((valor) => !valor)}
          className="h-9 px-4 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro transition-colors"
        >
          {mostrarFormulario ? "Cancelar" : "+ Nuevo usuario"}
        </button>
      </header>

      <main className="flex-1 p-6 overflow-y-auto flex flex-col gap-6">
        {mostrarFormulario && (
          <form onSubmit={manejarCrear} className="bg-superficie rounded-md shadow-tarjeta p-5 grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Campo etiqueta="Nombres">
              <input required value={formulario.nombres} onChange={(e) => setFormulario({ ...formulario, nombres: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Apellidos">
              <input required value={formulario.apellidos} onChange={(e) => setFormulario({ ...formulario, apellidos: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Tipo de documento">
              <input required value={formulario.tipoDocumento} onChange={(e) => setFormulario({ ...formulario, tipoDocumento: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Número de documento">
              <input required value={formulario.numeroDocumento} onChange={(e) => setFormulario({ ...formulario, numeroDocumento: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Correo corporativo">
              <input required type="email" value={formulario.correo} onChange={(e) => setFormulario({ ...formulario, correo: e.target.value })} className="campo" />
            </Campo>
            <Campo etiqueta="Rol">
              <select value={formulario.rol} onChange={(e) => setFormulario({ ...formulario, rol: e.target.value })} className="campo">
                {ROLES.map((rol) => (
                  <option key={rol} value={rol}>
                    {rol}
                  </option>
                ))}
              </select>
            </Campo>
            {formulario.rol !== "COLABORADOR" && (
              <Campo etiqueta="Contraseña inicial">
                <input
                  required
                  type="password"
                  value={formulario.password}
                  onChange={(e) => setFormulario({ ...formulario, password: e.target.value })}
                  placeholder="Mínimo 8 caracteres, letras y números"
                  className="campo"
                />
              </Campo>
            )}

            {error && (
              <p className="sm:col-span-2 text-sm text-peligroTexto bg-peligroFondo border border-peligro/30 rounded-sm px-3 py-2">
                {error}
              </p>
            )}

            <div className="sm:col-span-2">
              <button
                type="submit"
                disabled={guardando}
                className="h-10 px-5 rounded-sm bg-primario text-white text-sm font-semibold hover:bg-primarioOscuro disabled:opacity-60 transition-colors"
              >
                {guardando ? "Guardando…" : "Guardar usuario"}
              </button>
            </div>
          </form>
        )}

        <Tabla
          cargando={cargando}
          vacio="No hay usuarios registrados todavía."
          columnas={["Nombre", "Correo", "Rol", "Estado", ""]}
          filas={usuarios.map((usuario) => [
            <span key="nombre" className="font-medium">
              {usuario.nombres} {usuario.apellidos}
            </span>,
            <span key="correo" className="text-textoSuave">
              {usuario.correo}
            </span>,
            <span key="rol" className="text-textoSuave">
              {usuario.rol}
            </span>,
            <EstadoPill key="estado" activo={usuario.activo} />,
            <button
              key="accion"
              onClick={() => manejarCambiarEstado(usuario)}
              className="text-xs font-semibold text-primario hover:text-primarioOscuro"
            >
              {usuario.activo ? "Desactivar" : "Activar"}
            </button>,
          ])}
        />
      </main>
    </>
  );
}
