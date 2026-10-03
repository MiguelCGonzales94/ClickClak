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
# Correo distinto en cada ejecución: tras 5 logins fallidos el backend bloquea esa cuenta.
resultado "Login con credenciales malas" "401" "$(codigo -X POST https://localhost/api/auth/login -H 'Content-Type: application/json' -d "{\"correo\":\"verificacion.$(date +%s)@example.com\",\"password\":\"incorrecta123\"}")"

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

echo "== seguridad web (cabeceras, superficie expuesta y límites)"
cabeceras() { curl -ksI -m 20 "$1" | tr -d '\015'; }
for destino in "443:https://localhost/" "8443:https://localhost:8443/"; do
    puerto=${destino%%:*}; url=${destino#*:}
    encabezados=$(cabeceras "$url")
    tiene() { printf '%s
' "$encabezados" | grep -qi "$1" && echo "presente" || echo "ausente"; }
    resultado "[$puerto] X-Content-Type-Options: nosniff" "presente" "$(tiene '^x-content-type-options: nosniff')"
    resultado "[$puerto] X-Frame-Options: DENY" "presente" "$(tiene '^x-frame-options: deny')"
    resultado "[$puerto] Content-Security-Policy con default-src 'self'" "presente" "$(tiene "^content-security-policy: default-src 'self'")"
    resultado "[$puerto] Referrer-Policy" "presente" "$(tiene '^referrer-policy:')"
    resultado "[$puerto] Permissions-Policy limita sensores" "presente" "$(tiene '^permissions-policy:.*camera=()')"
    resultado "[$puerto] el servidor no revela su versión" "presente" "$(tiene '^server: nginx\s*$')"
done
# HSTS solo con certificado real: sobre el autofirmado dejaría el sitio inaccesible en los navegadores.
if [ -f "/etc/letsencrypt/live/$DOMINIO/fullchain.pem" ]; then ESPERADO_HSTS="presente"; else ESPERADO_HSTS="ausente"; fi
HSTS=$(cabeceras https://localhost/ | grep -qi '^strict-transport-security:' && echo presente || echo ausente)
resultado "HSTS acorde al certificado (esperado: $ESPERADO_HSTS)" "$ESPERADO_HSTS" "$HSTS"
resultado "API sin cabeceras duplicadas (nosniff una sola vez)" "1" "$(cabeceras https://localhost/api/incidencias/mias | grep -ci '^x-content-type-options:')"
resultado "Actuator health no se enruta" "404" "$(codigo https://localhost/actuator/health)"
resultado "Actuator prometheus no se enruta" "404" "$(codigo https://localhost/actuator/prometheus)"
resultado "Archivo oculto /.env no se sirve" "404" "$(codigo https://localhost/.env)"
resultado "Carpeta oculta /.git/config no se sirve" "404" "$(codigo https://localhost/.git/config)"
resultado "Método TRACE rechazado" "405" "$(codigo -X TRACE https://localhost/)"
resultado "Error de la API sin detalles internos" "1" "$(curl -ks -m 20 -X POST https://localhost/api/auth/login -H 'Content-Type: application/json' -d '{"correo": ' | grep -c 'cuerpo de la solicitud no es válido')"
# La recuperación de clave tiene su propio cupo (6 por minuto, ráfaga de 3); se prueba ella y no el
# login para no consumir el cupo del acceso real que se comprobó arriba.
PRIMERA=$(codigo -X POST https://localhost/api/auth/recuperacion/solicitar -H 'Content-Type: application/json' -d '{"correo":"nadie@example.com"}')
LIMITADO=0
for i in 1 2 3 4 5 6 7 8; do
    [ "$(codigo -X POST https://localhost/api/auth/recuperacion/solicitar -H 'Content-Type: application/json' -d '{"correo":"nadie@example.com"}')" = "429" ] && LIMITADO=1
done
resultado "Recuperación de clave: la primera petición pasa" "200" "$PRIMERA"
resultado "Recuperación de clave: la ráfaga recibe 429" "1" "$LIMITADO"

echo
if [ "$FALLOS" -eq 0 ]; then
    echo "Verificación completa: todo en orden."
else
    echo "Verificación con $FALLOS fallo(s)."
    exit 1
fi
