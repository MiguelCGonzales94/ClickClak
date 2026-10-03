# -*- coding: utf-8 -*-
"""Prueba de extremo a extremo por HTTP de la marcación y de la tardanza automática (OE3, OE4 y OE5).

Uso, con el backend en el perfil dev y el Postgres de Docker (clickclak-postgres, puerto 5434) levantados:

    python Docs/tecnica/evidencia/prueba_e2e_marcacion.py [URL_BASE]      (por defecto http://localhost:8080)

Cada ejecución crea sus propios datos de prueba, con un sufijo único, en la base de DESARROLLO:
un proyecto, una ubicación, un horario, dos usuarios con contraseña, sus dispositivos y una asignación.
Los usuarios de prueba tienen rol SUPERVISOR porque un colaborador no puede tener contraseña (su acceso es solo por
WebAuthn, que este script no puede ejercitar); la marcación no depende del rol del usuario autenticado.
No imprime contraseñas ni tokens. Devuelve código de salida distinto de cero si algún caso falla.
"""
import json
import re
import secrets
import string
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

sys.stdout.reconfigure(encoding="utf-8")
BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
SEMILLA = "backend/src/main/resources/db/dev/R__semilla_desarrollo.sql"
SUFIJO = time.strftime("%y%m%d%H%M%S")
LATITUD, LONGITUD = -12.046400, -77.042800  # punto de referencia de la ubicación de prueba
GRADO_LATITUD_M = 111_320.0  # metros por grado de latitud


def llamar(metodo, ruta, cuerpo=None, token=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    if cuerpo is not None:
        peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=30) as r:
            texto = r.read().decode()
            return r.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as e:
        texto = e.read().decode()
        try:
            return e.code, json.loads(texto)
        except json.JSONDecodeError:
            return e.code, {"error": texto}


def iniciar_sesion(correo, clave):
    estado, cuerpo = llamar("POST", "/api/auth/login", {"correo": correo, "password": clave})
    assert estado == 200, f"no se pudo iniciar sesión con {correo}: {estado}"
    _, perfil = llamar("GET", "/api/auth/yo", token=cuerpo["token"])
    return cuerpo["token"], perfil["id"]


def sql(consulta):
    salida = subprocess.check_output(
        ["docker", "exec", "clickclak-postgres", "psql", "-U", "clickclak", "-d", "clickclak", "-Atc", consulta],
        text=True)
    return salida.strip().splitlines()[0]


def crear_dispositivo(usuario_id):
    return int(sql(
        "INSERT INTO dispositivo (usuario_id, credential_id, clave_publica, contador_firma, nombre_dispositivo, activo) "
        f"VALUES ({usuario_id}, 'e2e-{SUFIJO}-{usuario_id}', 'clave-publica-de-prueba', 0, 'Dispositivo E2E', true) RETURNING id;"))


def clave_nueva():
    return "Pr" + "".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(12)) + "7"


def utc(texto_lima):
    """'2026-10-02 08:02' (hora de Lima, UTC-5) -> ISO en UTC."""
    local = datetime.strptime(texto_lima, "%Y-%m-%d %H:%M")
    return (local + timedelta(hours=5)).replace(tzinfo=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


resultados = []


def caso(id_, descripcion, esperado, obtenido, ok=None):
    ok = (esperado == obtenido) if ok is None else ok
    resultados.append((id_, descripcion, esperado, obtenido, "CORRECTO" if ok else "FALLÓ"))


def marcar(token, usuario_id, dispositivo_id, tipo, hora_utc, desplazamiento_norte_m=0.0, precision=10, uuid_cliente=None,
           longitud=LONGITUD):
    cuerpo = {
        "uuidCliente": uuid_cliente or str(uuid.uuid4()), "usuarioId": usuario_id, "dispositivoId": dispositivo_id,
        "tipoEvento": tipo, "horaEvento": hora_utc,
        "latitud": LATITUD + desplazamiento_norte_m / GRADO_LATITUD_M, "longitud": longitud, "precisionMetros": precision,
    }
    return llamar("POST", "/api/marcaciones", cuerpo, token), cuerpo


# ---------------------------------------------------------------- preparación
semilla = open(SEMILLA, encoding="utf-8").read()
clave_admin = re.search(r"contraseña:\s+(\S+)", semilla).group(1)
token_admin, _ = iniciar_sesion("admin@clickclak.local", clave_admin)

claves = {"a": clave_nueva(), "b": clave_nueva()}
ids = {}
for letra, doc in (("a", "7" + SUFIJO[-7:]), ("b", "8" + SUFIJO[-7:])):
    estado, usuario = llamar("POST", "/api/usuarios", {
        "nombres": f"Prueba{letra.upper()}", "apellidos": "E2E", "tipoDocumento": "DNI", "numeroDocumento": doc,
        "correo": f"e2e.{letra}.{SUFIJO}@clickclak.local", "rol": "SUPERVISOR", "password": claves[letra]}, token_admin)
    assert estado == 201, f"alta de usuario de prueba {letra}: {estado} {usuario}"
    ids[letra] = usuario["id"]

estado, proyecto = llamar("POST", "/api/proyectos", {"nombre": f"Prueba E2E {SUFIJO}", "cliente": "Cliente de prueba",
                                                     "fechaInicio": "2026-09-01"}, token_admin)
assert estado == 201, proyecto
estado, ubicacion = llamar("POST", "/api/ubicaciones", {"proyectoId": proyecto["id"], "nombre": f"Sede E2E {SUFIJO}",
                                                        "latitud": LATITUD, "longitud": LONGITUD,
                                                        "radioToleranciaMetros": 150}, token_admin)
assert estado == 201, ubicacion
estado, horario = llamar("POST", "/api/horarios", {"nombre": f"Horario E2E {SUFIJO}", "horaInicio": "08:00:00",
                                                   "horaFin": "17:00:00", "toleranciaMinutos": 10}, token_admin)
assert estado == 201, horario
estado, asignacion = llamar("POST", "/api/asignaciones", {"usuarioId": ids["a"], "proyectoId": proyecto["id"],
                                                          "ubicacionId": ubicacion["id"], "horarioId": horario["id"],
                                                          "fechaInicio": "2026-09-30"}, token_admin)
assert estado == 201, asignacion

token_a, id_a = iniciar_sesion(f"e2e.a.{SUFIJO}@clickclak.local", claves["a"])
token_b, id_b = iniciar_sesion(f"e2e.b.{SUFIJO}@clickclak.local", claves["b"])
disp_a, disp_b = crear_dispositivo(id_a), crear_dispositivo(id_b)

# ---------------------------------------------------------------- casos
(estado, r), cuerpo = marcar(token_a, id_a, disp_a, "ENTRADA", utc("2026-10-02 08:02"))
caso("E1", "Ingreso puntual dentro del radio (precisión 10 m): se registra y es VALIDO", "201 / VALIDO",
     f"{estado} / {r.get('estadoValidacion')}")
id_marcacion = r.get("id")
caso("E1b", "Un ingreso puntual no genera incidencia de tardanza", "0 tardanzas",
     f"{sum(1 for i in llamar('GET', f'/api/incidencias?usuarioId={id_a}', token=token_admin)[1] if i['tipo'] == 'TARDANZA')} tardanzas")

uuid_fijo = cuerpo["uuidCliente"]
cuerpo2 = dict(cuerpo, latitud=LATITUD + 0.05, precisionMetros=5)  # reenvío con datos alterados
estado2, r2 = llamar("POST", "/api/marcaciones", cuerpo2, token_a)
caso("E2", "Reenvío del mismo uuid con datos alterados: devuelve la marcación original, sin duplicar ni sobrescribir",
     f"mismo id {id_marcacion} y latitud original", f"id {r2.get('id')} y latitud {'original' if abs(r2.get('latitud', 0) - LATITUD) < 1e-6 else 'ALTERADA'}",
     ok=(r2.get("id") == id_marcacion and abs(r2.get("latitud", 0) - LATITUD) < 1e-6))
filas = sql("select count(*) from marcacion where uuid_cliente = '" + uuid_fijo + "'")
caso("E2b", "El uuid repetido no crea una segunda fila en la base", "1 fila(s)", f"{filas} fila(s)")

(estado, r), _ = marcar(token_a, id_a, disp_a, "SALIDA", utc("2026-10-02 17:00"), desplazamiento_norte_m=1000)
caso("E3", "Salida a 1 km de la sede: FUERA_DE_TOLERANCIA", "201 / FUERA_DE_TOLERANCIA", f"{estado} / {r.get('estadoValidacion')}")

(estado, r), _ = marcar(token_a, id_a, disp_a, "INICIO_REFRIGERIO", utc("2026-10-02 12:00"), precision=800)
caso("E4", "Precisión reportada de 800 m (mayor que 500 m): SOSPECHOSO", "201 / SOSPECHOSO", f"{estado} / {r.get('estadoValidacion')}")

(estado, r), _ = marcar(token_a, id_a, disp_a, "FIN_REFRIGERIO", utc("2026-10-02 13:00"), desplazamiento_norte_m=160, precision=50)
caso("E5", "A 160 m con precisión de 50 m y radio de 150 m (ambiguo por el error del GPS): OBSERVADO",
     "201 / OBSERVADO", f"{estado} / {r.get('estadoValidacion')}")

(estado, r), _ = marcar(token_a, id_a, disp_a, "ENTRADA", utc("2026-10-01 09:45"))
caso("E6", "Ingreso con 105 minutos de retraso (horario 08:00, tolerancia 10): se registra", "201", str(estado))
tardanzas = [i for i in llamar("GET", f"/api/incidencias?usuarioId={id_a}", token=token_admin)[1] if i["tipo"] == "TARDANZA"]
caso("E6b", "Se generó una incidencia TARDANZA automática en estado REGISTRADA", "1 tardanza REGISTRADA",
     f"{len(tardanzas)} tardanza(s), estados {sorted({i['estado'] for i in tardanzas})}",
     ok=(len(tardanzas) == 1 and tardanzas[0]["estado"] == "REGISTRADA"))
if tardanzas:
    detalle = llamar("GET", f"/api/incidencias/{tardanzas[0]['id']}", token=token_admin)[1]
    caso("E6c", "La incidencia automática nace con una fila de historial", "1 fila de historial",
         f"{len(detalle['historial'])} fila(s)", ok=(len(detalle["historial"]) == 1))
    auditoria = sql(f"select count(*) from bitacora_auditoria where entidad = 'incidencia' and entidad_id = {tardanzas[0]['id']} "
                    "and usuario_id is null and accion = 'CREACION'")
    caso("E6d", "...y una fila de auditoría con el sistema como autor (sin usuario)", "1 fila", f"{auditoria} fila(s)",
         ok=(auditoria == "1"))

ahora_mas_30 = (datetime.now(timezone.utc) + timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%SZ")
(estado, r), _ = marcar(token_a, id_a, disp_a, "ENTRADA", ahora_mas_30)
caso("E7", "Hora del evento 30 minutos en el futuro: se rechaza", "400", str(estado))

(estado, r), _ = marcar(token_b, id_b, disp_b, "ENTRADA", utc("2026-10-02 08:00"))
caso("E8", "Colaborador sin asignación vigente: se registra con SIN_ASIGNACION", "201 / SIN_ASIGNACION",
     f"{estado} / {r.get('estadoValidacion')}")

(estado, r), _ = marcar(token_a, id_a, disp_b, "ENTRADA", utc("2026-10-02 08:00"))
caso("E9", "Marcar con el dispositivo de otro colaborador: se rechaza", "403", str(estado))

(estado, r), _ = marcar(token_a, id_b, disp_a, "ENTRADA", utc("2026-10-02 08:00"))
caso("E10", "Marcar a nombre de otro usuario alterando el cuerpo: se rechaza", "403", str(estado))

(estado, r), _ = marcar(None, id_a, disp_a, "ENTRADA", utc("2026-10-02 08:00"))
caso("E11", "Marcar sin sesión: se rechaza", "401", str(estado))

# ---------------------------------------------------------------- informe
print(f"Prueba E2E de marcación · {BASE} · sufijo de datos {SUFIJO} · usuarios de prueba con rol SUPERVISOR\n")
print("| Caso | Descripción | Esperado | Obtenido | Resultado |")
print("|---|---|---|---|---|")
for fila in resultados:
    print("| " + " | ".join(str(x) for x in fila) + " |")
fallos = [f for f in resultados if f[4] != "CORRECTO"]
print(f"\nResumen: {len(resultados) - len(fallos)} de {len(resultados)} casos correctos.")
sys.exit(1 if fallos else 0)
