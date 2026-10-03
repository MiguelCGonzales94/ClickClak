# -*- coding: utf-8 -*-
"""Mide la proporción de código Java del proyecto (CLICKCLACK-65).

Uso, desde la raíz del repositorio:  python Docs/tecnica/medir-java.py

Cuenta líneas de código (sin líneas en blanco ni comentarios de línea o de bloque) de los archivos
versionados en git. Excluye los scripts generados del envoltorio de Maven (mvnw, mvnw.cmd) y los
archivos de bloqueo de dependencias. Las definiciones y sus límites están en medicion-java.md.
"""
import re
import subprocess
import sys
from collections import Counter

EXT_CODIGO = {"java", "ts", "tsx", "js", "css", "sql", "yml", "yaml", "properties", "xml"}


def versionados(prefijo):
    salida = subprocess.check_output(["git", "ls-files", prefijo], text=True, encoding="utf-8")
    return [f for f in salida.splitlines()
            if not f.endswith(("mvnw", "mvnw.cmd", "package-lock.json")) and "/.mvn/" not in f]


def lineas_de_codigo(ruta):
    ext = ruta.rsplit(".", 1)[-1].lower()
    texto = open(ruta, encoding="utf-8", errors="replace").read()
    if ext in ("java", "ts", "tsx", "js", "css"):
        texto = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)
        marca = "//"
    elif ext == "sql":
        texto = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)
        marca = "--"
    elif ext in ("yml", "yaml", "properties"):
        marca = "#"
    elif ext == "xml":
        texto = re.sub(r"<!--.*?-->", "", texto, flags=re.S)
        marca = None
    else:
        return ext, 0
    cuenta = 0
    for linea in texto.splitlines():
        s = linea.strip()
        if s and not (marca and s.startswith(marca)):
            cuenta += 1
    return ext, cuenta


def medir(prefijo, zona_base="raiz"):
    """Devuelve {(zona, extensión): líneas}; zona es 'main', 'test' o la zona base del prefijo."""
    total = Counter()
    for ruta in versionados(prefijo):
        ext, n = lineas_de_codigo(ruta)
        if n == 0 or ext not in EXT_CODIGO:
            continue
        zona = "test" if "/src/test/" in ruta else ("main" if "/src/main/" in ruta else zona_base)
        if ext in ("ts", "tsx", "js", "css") and ".test." in ruta:
            zona = "test"
        total[(zona, ext)] += n
    return total


def porcentaje(parte, todo):
    return 100.0 * parte / todo if todo else 0.0


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    back = medir("backend")
    front = Counter()
    for d in ("frontend-admin", "frontend-campo"):
        front.update(medir(d + "/src", zona_base="main"))

    java_main = back[("main", "java")]
    java_test = back[("test", "java")]
    otros_main = sum(v for (z, e), v in back.items() if z == "main" and e != "java")
    otros_raiz = sum(v for (z, e), v in back.items() if z == "raiz")
    ts_main = sum(v for (z, e), v in front.items() if z == "main")
    ts_test = sum(v for (z, e), v in front.items() if z == "test")

    print("Líneas de código (sin blancos ni comentarios), archivos versionados:\n")
    print(f"backend src/main: Java {java_main}, otros (SQL, YAML) {otros_main}")
    print(f"backend src/test: Java {java_test}")
    print(f"backend raíz (pom.xml): {otros_raiz}")
    print(f"frontends src: producción {ts_main}, pruebas {ts_test}\n")

    print("| Definición | Java | Total | % Java |")
    print("|---|---|---|---|")
    filas = [
        ("A. Back-end, código de producción (src/main)", java_main, java_main + otros_main),
        ("B. Back-end completo (producción, pruebas y pom.xml)", java_main + java_test,
         java_main + java_test + otros_main + otros_raiz),
        ("C. Todo el código de aplicación sin pruebas (back-end y frontends)", java_main,
         java_main + otros_main + ts_main),
        ("D. Todo el código de aplicación con pruebas", java_main + java_test,
         java_main + java_test + otros_main + ts_main + ts_test),
    ]
    for nombre, java, total in filas:
        print(f"| {nombre} | {java} | {total} | {porcentaje(java, total):.1f} % |")


if __name__ == "__main__":
    main()
