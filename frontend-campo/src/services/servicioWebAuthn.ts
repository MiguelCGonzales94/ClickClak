import { clienteApi } from "./clienteApi";
import type { RespuestaLogin, RespuestaOpcionesWebAuthn } from "../types/api";

/**
 * PublicKeyCredential.parseCreationOptionsFromJSON/parseRequestOptionsFromJSON y
 * credential.toJSON() son métodos nativos del navegador (WebAuthn L3) — evitan tener que
 * convertir a mano los campos base64url a ArrayBuffer y viceversa. Se referencian como
 * `any` porque los tipos DOM del proyecto pueden no incluirlos todavía según la versión
 * de TypeScript, sin que eso afecte su disponibilidad real en el navegador.
 */
type PublicKeyCredentialConJson = PublicKeyCredential & { toJSON(): unknown };

export function soportaWebAuthn(): boolean {
  const pkc = (window as unknown as { PublicKeyCredential?: unknown }).PublicKeyCredential as
    | { parseCreationOptionsFromJSON?: unknown; parseRequestOptionsFromJSON?: unknown }
    | undefined;
  return (
    typeof pkc !== "undefined" &&
    typeof pkc.parseCreationOptionsFromJSON === "function" &&
    typeof pkc.parseRequestOptionsFromJSON === "function"
  );
}

export const servicioWebAuthn = {
  /** HU01/HU05: un Supervisor/RRHH autenticado enrola el dispositivo del técnico presente. */
  async registrarDispositivo(usuarioId: number, nombreDispositivo: string, token: string): Promise<void> {
    const inicio = await clienteApi.post<RespuestaOpcionesWebAuthn>(
      "/api/webauthn/registro/iniciar",
      { usuarioId },
      token,
    );

    const opciones = JSON.parse(inicio.opcionesJson) as { publicKey: PublicKeyCredentialCreationOptionsJSON };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const opcionesCreacion = (PublicKeyCredential as any).parseCreationOptionsFromJSON(opciones.publicKey);

    const credencial = (await navigator.credentials.create({
      publicKey: opcionesCreacion,
    })) as PublicKeyCredentialConJson;

    if (!credencial) {
      throw new Error("El navegador no devolvió una credencial");
    }

    await clienteApi.post<void>(
      "/api/webauthn/registro/finalizar",
      {
        idSolicitud: inicio.idSolicitud,
        credencialJson: JSON.stringify(credencial.toJSON()),
        nombreDispositivo: nombreDispositivo || undefined,
      },
      token,
    );
  },

  /** HU05: login biométrico. Público — es el propio mecanismo de acceso, sin JWT previo. */
  async autenticar(correo: string): Promise<RespuestaLogin> {
    const inicio = await clienteApi.post<RespuestaOpcionesWebAuthn>("/api/webauthn/autenticacion/iniciar", {
      correo,
    });

    const opciones = JSON.parse(inicio.opcionesJson) as { publicKey: PublicKeyCredentialRequestOptionsJSON };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const opcionesSolicitud = (PublicKeyCredential as any).parseRequestOptionsFromJSON(opciones.publicKey);

    const credencial = (await navigator.credentials.get({
      publicKey: opcionesSolicitud,
    })) as PublicKeyCredentialConJson;

    if (!credencial) {
      throw new Error("El navegador no devolvió una credencial");
    }

    return clienteApi.post<RespuestaLogin>("/api/webauthn/autenticacion/finalizar", {
      idSolicitud: inicio.idSolicitud,
      credencialJson: JSON.stringify(credencial.toJSON()),
    });
  },
};
