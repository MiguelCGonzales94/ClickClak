#!/bin/sh
# Verificación rápida del despliegue (prueba de humo). Se ejecuta EN la VM, desde infra/:
#     sh scripts/verificar-despliegue.sh
# Comprueba contenedores, respuestas del proxy y un acceso real con el administrador inicial.
# No imprime la contraseña ni el token. Termina con código distinto de 0 si algo falla.
set -u
cd "$(dirname "$0")/.."

FALLOS=0
DOMINIO=$(sed -n 's/^DOMINIO=//p' .env)
resultado() { # $1 = descripción, $2 = esperado, $3 = obtenido
    if [ "$2" = "$3" ]; then
        printf 'OK     %-58s %s\n' "$1" "$3"
    else
        printf 'FALLA  %-58s esperado %s, obtenido %s\n' "$1" "$2" "$3"
        FALLOS=$((FALLOS + 1))
    fi
}
codigo() { curl -ks -m 20 -o /dev/null -w '%{http_code}' "$@"; }

echo "== contenedores"
for servicio in postgres backend frontend-admin frontend-campo proxy; do
    estado=$(sudo docker ps --filter "name=clickclak-$servicio-1" --format '{{.Status}}')
    case "$estado" in
        *unhealthy*|"")  resultado "contenedor $servicio" "activo" "${estado:-caído}" ;;
        Up*)             resultado "contenedor $servicio" "activo" "activo" ;;
        *)               resultado "contenedor $servicio" "activo" "$estado" ;;
    esac
done

echo "== proxy (HTTPS con certificado temporal: curl -k)"
# Con el Host real: es lo que ve un usuario que entra por el nombre DNS.
resultado "HTTP 80 redirige a HTTPS" "301" "$(codigo -H "Host: $DOMINIO" http://localhost/)"
resultado "HTTPS 443: app de campo" "200" "$(codigo https://localhost/)"
resultado "HTTPS 8443: panel administrativo" "200" "$(codigo https://localhost:8443/)"
resultado "API sin token (443)" "401" "$(codigo https://localhost/api/incidencias/mias)"
resultado "API sin token (8443)" "401" "$(codigo https://localhost:8443/api/incidencias/mias)"
resultado "Login con credenciales malas" "401" "$(codigo -X POST https://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"correo":"nadie@example.com","password":"incorrecta123"}')"

echo "== acceso real con el administrador inicial"
CREDENCIALES="$HOME/credenciales-admin-inicial.txt"
if [ -f "$CREDENCIALES" ]; then
    CORREO=$(sed -n 's/^correo: //p' "$CREDENCIALES")
    CLAVE=$(sed -n 's/^contraseña: //p' "$CREDENCIALES")
    RESPUESTA=$(curl -ks -m 20 -X POST https://localhost/api/auth/login -H 'Content-Type: application/json' \
        -d "{\"correo\":\"$CORREO\",\"password\":\"$CLAVE\"}")
    TOKEN=$(printf '%s' "$RESPUESTA" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
    if [ -n "$TOKEN" ]; then
        resultado "login del administrador" "correcto" "correcto"
        resultado "rol devuelto" "RRHH_ADMIN" "$(printf '%s' "$RESPUESTA" | sed -n 's/.*"rol":"\([^"]*\)".*/\1/p')"
        for ruta in /api/auth/yo /api/incidencias /api/incidencias/mias /api/usuarios; do
            resultado "GET $ruta con token" "200" "$(codigo -H "Authorization: Bearer $TOKEN" "https://localhost$ruta")"
        done
    else
        resultado "login del administrador" "correcto" "falló"
    fi
else
    echo "OMITIDO  no existe $CREDENCIALES (ejecute scripts/crear-admin-inicial.sh)"
fi

echo
if [ "$FALLOS" -eq 0 ]; then
    echo "Verificación completa: todo en orden."
else
    echo "Verificación con $FALLOS fallo(s)."
    exit 1
fi
