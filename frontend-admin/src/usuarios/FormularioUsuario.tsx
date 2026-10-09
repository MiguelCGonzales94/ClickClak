import { useState, type FormEvent } from "react";
import { Campo } from "../components/Campo";
import { ErrorHttp } from "../services/clienteApi";
import { servicioUsuarios } from "../services/servicioUsuarios";
import type { RespuestaUsuario } from "../types/api";
import { cambioDeRol, esColaborador, ROLES, TIPOS_DOCUMENTO, validarDocumento } from "./reglas";

interface Props {
  token: string;
  /** Si viene, el formulario edita ese usuario; si no, crea uno nuevo. */
  usuario?: RespuestaUsuario;
  alGuardar: (usuario: RespuestaUsuario) => void;
  alCancelar: () => void;
}

interface Valores {
  nombres: string;
  apellidos: string;
  tipoDocumento: string;
  numeroDocumento: string;
  correo: string;
  rol: string;
  password: string;
}

function valoresIniciales(usuario?: RespuestaUsuario): Valores {
  return {
    nombres: usuario?.nombres ?? "",
    apellidos: usuario?.apellidos ?? "",
    tipoDocumento: usuario?.tipoDocumento ?? "DNI",
    numeroDocumento: usuario?.numeroDocumento ?? "",
    correo: usuario?.correo ?? "",
    rol: usuario?.rol ?? "COLABORADOR",
    password: "",
  };
}

/** HU04: alta y edición comparten formulario; la contraseña solo aparece cuando el rol la necesita. */
export function FormularioUsuario({ token, usuario, alGuardar, alCancelar }: Props) {
  const editando = usuario !== undefined;
  const [valores, setValores] = useState<Valores>(() => valoresIniciales(usuario));
  const [errorDocumento, setErrorDocumento] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const cambio = editando ? cambioDeRol(usuario.rol, valores.rol) : "sin-cambio";
  // Al crear, todo rol distinto de colaborador pide clave. Al editar, solo si el rol pasa de colaborador a otro.
  const pideClave = editando ? cambio === "exige-clave" : !esColaborador(valores.rol);

  // Si el usuario ya trae un tipo fuera de la lista (datos anteriores al selector), se conserva como opción.
  const tipos = TIPOS_DOCUMENTO.some((tipo) => tipo.valor === valores.tipoDocumento)
    ? TIPOS_DOCUMENTO
    : [...TIPOS_DOCUMENTO, { valor: valores.tipoDocumento, etiqueta: valores.tipoDocumento }];

  function actualizar<K extends keyof Valores>(campo: K, valor: Valores[K]) {
    setValores((anteriores) => ({ ...anteriores, [campo]: valor }));
  }

  async function manejarEnvio(evento: FormEvent) {
    evento.preventDefault();
    setError(null);

    const problema = validarDocumento(valores.tipoDocumento, valores.numeroDocumento);
    setErrorDocumento(problema);
    if (problema) return;

    const datos = {
      nombres: valores.nombres.trim(),
      apellidos: valores.apellidos.trim(),
      tipoDocumento: valores.tipoDocumento,
      numeroDocumento: valores.numeroDocumento.trim(),
      correo: valores.correo.trim(),
      rol: valores.rol,
    };
    const password = pideClave ? valores.password : undefined;

    setGuardando(true);
    try {
      const guardado = editando
        ? await servicioUsuarios.editar(token, usuario.id, { ...datos, password })
        : await servicioUsuarios.registrar(token, { ...datos, password });
      alGuardar(guardado);
    } catch (err) {
      setError(err instanceof ErrorHttp ? err.message : "No se pudo guardar el usuario");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={manejarEnvio} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <Campo etiqueta="Nombres">
        <input required maxLength={100} value={valores.nombres} onChange={(e) => actualizar("nombres", e.target.value)} className="campo" />
      </Campo>
      <Campo etiqueta="Apellidos">
        <input required maxLength={100} value={valores.apellidos} onChange={(e) => actualizar("apellidos", e.target.value)} className="campo" />
      </Campo>
      <Campo etiqueta="Tipo de documento">
        <select
          value={valores.tipoDocumento}
          onChange={(e) => {
            actualizar("tipoDocumento", e.target.value);
            setErrorDocumento(null);
          }}
          className="campo"
        >
          {tipos.map((tipo) => (
            <option key={tipo.valor} value={tipo.valor}>
              {tipo.etiqueta}
            </option>
          ))}
        </select>
      </Campo>
      <div>
        <Campo etiqueta="Número de documento">
          <input
            required
            maxLength={20}
            value={valores.numeroDocumento}
            onChange={(e) => {
              actualizar("numeroDocumento", e.target.value);
              setErrorDocumento(null);
            }}
            aria-invalid={errorDocumento !== null}
            className="campo"
          />
        </Campo>
        {errorDocumento && <p className="mt-1 text-xs text-peligroTexto">{errorDocumento}</p>}
      </div>
      <Campo etiqueta="Correo corporativo">
        <input required type="email" maxLength={150} value={valores.correo} onChange={(e) => actualizar("correo", e.target.value)} className="campo" />
      </Campo>
      <Campo etiqueta="Rol">
        <select value={valores.rol} onChange={(e) => actualizar("rol", e.target.value)} className="campo">
          {ROLES.map((rol) => (
            <option key={rol} value={rol}>
              {rol}
            </option>
          ))}
        </select>
      </Campo>

      {pideClave && (
        <div className="sm:col-span-2">
          <Campo etiqueta={editando ? "Contraseña inicial del nuevo rol" : "Contraseña inicial"}>
            <input
              required
              type="password"
              autoComplete="new-password"
              maxLength={72}
              value={valores.password}
              onChange={(e) => actualizar("password", e.target.value)}
              placeholder="Mínimo 8 caracteres, letras y números"
              className="campo"
            />
          </Campo>
          {editando && (
            <p className="mt-1 text-xs text-textoSuave">
              Este usuario no tenía contraseña (acceso por huella). Con el nuevo rol entrará con correo y contraseña.
            </p>
          )}
        </div>
      )}
      {editando && cambio === "borra-clave" && (
        <p className="sm:col-span-2 rounded-sm border border-advertencia/30 bg-advertenciaFondo px-3 py-2 text-sm text-advertenciaTexto">
          Al pasar a COLABORADOR se borrará su contraseña: desde ahora entrará solo con su dispositivo.
        </p>
      )}

      {error && (
        <p role="alert" className="sm:col-span-2 rounded-sm border border-peligro/30 bg-peligroFondo px-3 py-2 text-sm text-peligroTexto">
          {error}
        </p>
      )}

      <div className="flex gap-3 sm:col-span-2">
        <button
          type="submit"
          disabled={guardando}
          className="h-10 rounded-sm bg-primario px-5 text-sm font-semibold text-white transition-colors hover:bg-primarioOscuro disabled:opacity-60"
        >
          {guardando ? "Guardando…" : editando ? "Guardar cambios" : "Crear usuario"}
        </button>
        <button
          type="button"
          onClick={alCancelar}
          className="h-10 rounded-sm border border-borde px-5 text-sm font-semibold text-texto hover:bg-fondo"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
