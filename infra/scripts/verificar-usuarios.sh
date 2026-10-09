#!/bin/sh
# Verifica la gestión de usuarios (HU04) contra el sistema desplegado: el corte de sesión al
# desactivar, la clave temporal obligatoria y la eliminación sin historial. Crea un usuario
# temporal propio de la ejecución y lo borra al terminar; no toca a ningún usuario real. No imprime
# contraseñas ni tokens. Se ejecuta EN la VM, desde infra/ (lo invoca también verificar-despliegue.sh):
#     sh scripts/verificar-usuarios.sh
# Necesita un administrador: usa TOKEN_ADMIN si viene en el entorno; si no, inicia sesión con
# ~/credenciales-admin-inicial.txt. Otra URL (por ejemplo un backend local):
#     URL_BASE=http://localhost:8080 sh scripts/verificar-usuarios.sh
# Termina con código distinto de 0 si algo falla.
set -u

URL_BASE="${URL_BASE:-https://localhost}"
FALLOS=0
resultado() { # $1 = descripción, $2 = esperado, $3 = obtenido
    if [ "$2" = "$3" ]; then
        printf 'OK     %-58s %s\n' "$1" "$3"
    else
        printf 'FALLA  %-58s esperado %s, obtenido %s\n' "$1" "$2" "$3"
        FALLOS=$((FALLOS + 1))
    fi
}
# El proxy limita el login a 10 por minuto con ráfaga de 5: este script hace solo 3 (más el del
# administrador si no trae TOKEN_ADMIN), para dejar margen a verificar-despliegue.sh, que ya usó 2.
# Código HTTP; el cuerpo de la última respuesta queda en $CUERPO por si hace falta leerlo.
CUERPO=$(mktemp)
CABECERAS=$(mktemp)
trap 'rm -f "$CUERPO" "$CABECERAS"' EXIT
llamar() { curl -ks -m 20 -D "$CABECERAS" -o "$CUERPO" -w '%{http_code}' "$@"; }
campo() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "$CUERPO"; }
numero() { sed -n "s/.*\"$1\":\([0-9]*\).*/\1/p" "$CUERPO"; }

TOKEN_ADMIN="${TOKEN_ADMIN:-}"
if [ -z "$TOKEN_ADMIN" ]; then
    CREDENCIALES="$HOME/credenciales-admin-inicial.txt"
    if [ ! -f "$CREDENCIALES" ]; then
        echo "OMITIDO  sin TOKEN_ADMIN ni $CREDENCIALES (ejecute scripts/crear-admin-inicial.sh)"
        exit 0
    fi
    CORREO_ADMIN=$(sed -n 's/^correo: //p' "$CREDENCIALES")
    CLAVE_ADMIN=$(sed -n 's/^contraseña: //p' "$CREDENCIALES")
    llamar -X POST "$URL_BASE/api/auth/login" -H 'Content-Type: application/json' \
        -d "{\"correo\":\"$CORREO_ADMIN\",\"password\":\"$CLAVE_ADMIN\"}" > /dev/null
    TOKEN_ADMIN=$(campo token)
fi
if [ -z "$TOKEN_ADMIN" ]; then
    echo "FALLA  no se pudo iniciar sesión como administrador"
    exit 1
fi
ADMIN="Authorization: Bearer $TOKEN_ADMIN"
llamar -H "$ADMIN" "$URL_BASE/api/auth/yo" > /dev/null
ID_ADMIN=$(numero id)

MARCA=$(date +%s)
CORREO="verificacion.usuarios.$MARCA@example.com"
DOCUMENTO=$(printf '%s' "$MARCA" | tail -c 8)
# Letra al inicio y dígito al final: cumple la política (letras y números) sin importar el azar.
CLAVE_INICIAL="Vf$(openssl rand -hex 8)9"

echo "== alta y búsqueda"
resultado "el administrador crea un supervisor temporal" "201" "$(llamar -X POST "$URL_BASE/api/usuarios" -H "$ADMIN" -H 'Content-Type: application/json' \
    -d "{\"nombres\":\"Verificacion\",\"apellidos\":\"Despliegue\",\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"$DOCUMENTO\",\"correo\":\"$CORREO\",\"rol\":\"SUPERVISOR\",\"password\":\"$CLAVE_INICIAL\"}")"
ID=$(numero id)
if [ -z "$ID" ]; then
    echo "FALLA  no se obtuvo el id del usuario temporal; se detiene la verificación"
    exit 1
fi
# Pase lo que pase, el usuario temporal se intenta borrar al salir (si ya se borró, no hay nada que hacer).
trap 'curl -ks -m 20 -o /dev/null -X DELETE -H "$ADMIN" "$URL_BASE/api/usuarios/$ID"; rm -f "$CUERPO" "$CABECERAS"' EXIT
resultado "su estado de cuenta es ACTIVA" "ACTIVA" "$(campo estadoCuenta)"
resultado "la búsqueda paginada lo encuentra" "200" "$(llamar -H "$ADMIN" "$URL_BASE/api/usuarios/buscar?q=$CORREO")"
resultado "la búsqueda devuelve exactamente 1 resultado" "1" "$(numero total)"

echo "== sesión del usuario y clave temporal"
resultado "el usuario inicia sesión con su clave" "200" "$(llamar -X POST "$URL_BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"correo\":\"$CORREO\",\"password\":\"$CLAVE_INICIAL\"}")"
TOKEN_USUARIO=$(campo token)
USUARIO="Authorization: Bearer $TOKEN_USUARIO"
resultado "con su sesión abierta puede listar usuarios" "200" "$(llamar -H "$USUARIO" "$URL_BASE/api/usuarios")"
resultado "restablecer la clave devuelve la temporal" "200" "$(llamar -X POST -H "$ADMIN" "$URL_BASE/api/usuarios/$ID/restablecer-clave")"
CLAVE_TEMPORAL=$(campo claveTemporal)
resultado "la temporal viaja con Cache-Control: no-store" "1" "$(grep -ci '^cache-control:.*no-store' "$CABECERAS")"
resultado "la clave temporal tiene 12 caracteres" "12" "${#CLAVE_TEMPORAL}"
resultado "su estado pasa a CLAVE_PENDIENTE" "CLAVE_PENDIENTE" "$(llamar -H "$ADMIN" "$URL_BASE/api/usuarios/$ID" > /dev/null; campo estadoCuenta)"
resultado "la sesión que ya tenía queda limitada (403)" "403" "$(llamar -H "$USUARIO" "$URL_BASE/api/usuarios")"
resultado "el 403 indica CLAVE_PENDIENTE" "CLAVE_PENDIENTE" "$(campo codigo)"
resultado "aun así puede consultar su perfil" "200" "$(llamar -H "$USUARIO" "$URL_BASE/api/auth/yo")"
resultado "la clave anterior ya no sirve para entrar" "401" "$(llamar -X POST "$URL_BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"correo\":\"$CORREO\",\"password\":\"$CLAVE_INICIAL\"}")"
resultado "entra con la temporal y se le avisa" "true" "$(llamar -X POST "$URL_BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"correo\":\"$CORREO\",\"password\":\"$CLAVE_TEMPORAL\"}" > /dev/null; sed -n 's/.*"debeCambiarClave":\(true\|false\).*/\1/p' "$CUERPO")"
TOKEN_TEMPORAL=$(campo token)

echo "== corte de sesión al desactivar y eliminación"
resultado "el administrador lo desactiva con motivo" "200" "$(llamar -X POST "$URL_BASE/api/usuarios/$ID/desactivar" -H "$ADMIN" -H 'Content-Type: application/json' -d '{"motivo":"verificacion de despliegue"}')"
resultado "su estado pasa a INACTIVA" "INACTIVA" "$(campo estadoCuenta)"
resultado "su token vigente deja de servir de inmediato" "401" "$(llamar -H "Authorization: Bearer $TOKEN_TEMPORAL" "$URL_BASE/api/auth/yo")"
resultado "el administrador no puede eliminarse a sí mismo" "409" "$(llamar -X DELETE -H "$ADMIN" "$URL_BASE/api/usuarios/$ID_ADMIN")"
resultado "sin historial, el usuario temporal se elimina" "204" "$(llamar -X DELETE -H "$ADMIN" "$URL_BASE/api/usuarios/$ID")"
resultado "y ya no existe" "404" "$(llamar -H "$ADMIN" "$URL_BASE/api/usuarios/$ID")"

echo
if [ "$FALLOS" -eq 0 ]; then
    echo "Gestión de usuarios: todo en orden."
else
    echo "Gestión de usuarios con $FALLOS fallo(s)."
    exit 1
fi
